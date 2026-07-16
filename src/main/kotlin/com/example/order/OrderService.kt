package com.example.order

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import org.springframework.core.io.ClassPathResource
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val kafkaTemplate: KafkaTemplate<String, JsonNode>,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val NEW_ORDERS_TOPIC = "new-orders"
        private const val WIP_ORDERS_TOPIC = "wip-orders"
        private val ITEM_NAME_REGEX = Regex("^[A-Za-z]{2,10}$")
    }

    private val serviceName = this::class.simpleName
    private val wipOrdersSchema = JsonSchema(ClassPathResource("jsonschema/WipOrders.json").inputStream.readBytes().toString(Charsets.UTF_8))

    init {
        println("$serviceName started running..")
    }

    @KafkaListener(topics = [NEW_ORDERS_TOPIC])
    fun placeOrder(orderRequest: JsonNode) {
        println("[$serviceName] Received message on topic $NEW_ORDERS_TOPIC - $orderRequest")

        validateOrderRequest(orderRequest)

        val orderToProcess = objectMapper.createObjectNode()
            .put("id", orderRequest.required("id").intValue())
            .put("status", "PROCESSING")
        kafkaTemplate.send(
            WIP_ORDERS_TOPIC,
            orderRequest.required("id").asText(),
            JsonSchemaUtils.envelope(wipOrdersSchema, orderToProcess)
        )
        println("[$serviceName] Sent message to topic $WIP_ORDERS_TOPIC - $orderToProcess")
    }

    private fun validateOrderRequest(orderRequest: JsonNode) {
        val id = orderRequest.required("id").intValue()
        require(id in 1..100) { "Order id must be between 1 and 100" }
        orderRequest.required("orderItems").forEachIndexed { index, item -> validateItem(index, item) }
    }

    private fun validateItem(index: Int, item: JsonNode) {
        val name = item.required("name").textValue()
        require(name.length in 2..10) {
            "Order item at index $index must have a name between 2 and 10 characters"
        }
        require(ITEM_NAME_REGEX.matches(name)) {
            "Order item at index $index must have a name matching ${ITEM_NAME_REGEX.pattern}"
        }
        require(item.required("price").intValue() >= 1000) {
            "Order item at index $index must have a price greater than or equal to 1000"
        }
    }
}
