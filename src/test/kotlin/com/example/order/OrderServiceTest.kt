package com.example.order

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.kafka.core.KafkaTemplate

class OrderServiceTest {
    private val objectMapper = ObjectMapper()

    @Suppress("UNCHECKED_CAST")
    private val kafkaTemplate = mock(KafkaTemplate::class.java) as KafkaTemplate<String, JsonNode>
    private val service = OrderService(kafkaTemplate, objectMapper)

    @Test
    fun `publishes processing order for valid request`() {
        service.placeOrder(orderRequest())

        val orderCaptor = ArgumentCaptor.forClass(JsonNode::class.java)
        verify(kafkaTemplate).send(org.mockito.Mockito.eq("wip-orders"), org.mockito.Mockito.eq("1"), orderCaptor.capture())
        assertEquals(1, orderCaptor.value.path("payload").path("id").intValue())
        assertEquals("PROCESSING", orderCaptor.value.path("payload").path("status").textValue())
    }

    @Test
    fun `rejects order ids outside JSON schema range`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.placeOrder(orderRequest(id = 101))
        }

        assertEquals("Order id must be between 1 and 100", exception.message)
        verify(kafkaTemplate, never()).send(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any())
    }

    @Test
    fun `rejects item names that do not match JSON schema`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.placeOrder(orderRequest(name = "a1"))
        }

        assertEquals("Order item at index 0 must have a name matching ^[A-Za-z]{2,10}$", exception.message)
        verify(kafkaTemplate, never()).send(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any())
    }

    @Test
    fun `rejects item prices below JSON schema minimum`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.placeOrder(orderRequest(price = 999))
        }

        assertEquals("Order item at index 0 must have a price greater than or equal to 1000", exception.message)
        verify(kafkaTemplate, never()).send(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any())
    }

    private fun orderRequest(id: Int = 1, name: String = "IPhone", price: Int = 5000): JsonNode =
        objectMapper.readTree(
            """{"id":$id,"orderItems":[{"id":10,"name":"$name","quantity":1,"price":$price}]}"""
        )
}
