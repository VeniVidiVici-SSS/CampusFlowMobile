package com.amazon.campusflow.data

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.sdk.kotlin.services.dynamodb.model.PutItemRequest
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.ScanRequest
import aws.sdk.kotlin.services.dynamodb.model.DeleteItemRequest
import com.amazon.campusflow.BuildConfig
import java.io.InputStream
import java.util.UUID

class AwsService {
    private val region = BuildConfig.AWS_REGION
    
    private val credentials = object : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes): Credentials {
            return Credentials(
                accessKeyId = BuildConfig.AWS_ACCESS_KEY_ID,
                secretAccessKey = BuildConfig.AWS_SECRET_ACCESS_KEY
            )
        }
    }

    private val s3Client = S3Client {
        region = this@AwsService.region
        credentialsProvider = credentials
    }

    private val dynamoDbClient = DynamoDbClient {
        region = this@AwsService.region
        credentialsProvider = credentials
    }

    suspend fun uploadScheduleToS3(inputStream: InputStream): String {
        val bucketName = BuildConfig.AWS_S3_BUCKET_NAME
        val fileName = "schedule-${UUID.randomUUID()}.xlsx"
        val bytes = inputStream.readBytes()
        val request = PutObjectRequest {
            bucket = bucketName
            key = "uploads/$fileName"
            body = ByteStream.fromBytes(bytes)
        }
        s3Client.putObject(request)
        return "s3://$bucketName/uploads/$fileName"
    }

    // --- DynamoDB Operations for Classes ---

    suspend fun getAllClasses(): List<ScheduleEvent> {
        val request = ScanRequest {
            tableName = "CampusFlow_Classes"
        }
        val response = dynamoDbClient.scan(request)
        return response.items?.map { item ->
            ScheduleEvent(
                courseName = item["courseName"]?.asS() ?: "",
                dayOfWeek = item["dayOfWeek"]?.asS() ?: "",
                startTime = item["startTime"]?.asS() ?: "",
                location = item["location"]?.asS() ?: "",
                startDateMillis = item["startDateMillis"]?.asN()?.toLong() ?: 0L,
                endDateMillis = item["endDateMillis"]?.asN()?.toLong() ?: 0L
            )
        } ?: emptyList()
    }

    suspend fun getEvent(courseName: String, dayOfWeek: String): ScheduleEvent? {
        val all = getAllClasses()
        return all.find { it.courseName == courseName && it.dayOfWeek == dayOfWeek }
    }

    suspend fun insertClasses(events: List<ScheduleEvent>) {
        events.forEach { event ->
            val request = PutItemRequest {
                tableName = "CampusFlow_Classes"
                item = mapOf(
                    "courseName" to AttributeValue.S(event.courseName),
                    "dayOfWeek" to AttributeValue.S(event.dayOfWeek),
                    "startTime" to AttributeValue.S(event.startTime),
                    "location" to AttributeValue.S(event.location),
                    "startDateMillis" to AttributeValue.N(event.startDateMillis.toString()),
                    "endDateMillis" to AttributeValue.N(event.endDateMillis.toString())
                )
            }
            dynamoDbClient.putItem(request)
        }
    }

    suspend fun deleteClass(courseName: String, dayOfWeek: String) {
        val request = DeleteItemRequest {
            tableName = "CampusFlow_Classes"
            key = mapOf(
                "courseName" to AttributeValue.S(courseName),
                "dayOfWeek" to AttributeValue.S(dayOfWeek)
            )
        }
        dynamoDbClient.deleteItem(request)
    }

    // --- DynamoDB Operations for Mess Menu ---

    suspend fun getAllMessMenus(): List<MessMenuEvent> {
        val request = ScanRequest {
            tableName = "CampusFlow_MessMenu"
        }
        val response = dynamoDbClient.scan(request)
        return response.items?.map { item ->
            MessMenuEvent(
                mealType = item["mealType"]?.asS() ?: "",
                dayOfWeek = item["dayOfWeek"]?.asS() ?: "",
                time = item["time"]?.asS() ?: "",
                menuItems = item["menuItems"]?.asS() ?: "",
                startDateMillis = item["startDateMillis"]?.asN()?.toLong() ?: 0L
            )
        } ?: emptyList()
    }

    suspend fun getMessEvent(mealType: String, dayOfWeek: String): MessMenuEvent? {
        val all = getAllMessMenus()
        return all.find { it.mealType == mealType && it.dayOfWeek == dayOfWeek }
    }

    suspend fun insertMessMenus(events: List<MessMenuEvent>) {
        events.forEach { event ->
            val request = PutItemRequest {
                tableName = "CampusFlow_MessMenu"
                item = mapOf(
                    "mealType" to AttributeValue.S(event.mealType),
                    "dayOfWeek" to AttributeValue.S(event.dayOfWeek),
                    "time" to AttributeValue.S(event.time),
                    "menuItems" to AttributeValue.S(event.menuItems),
                    "startDateMillis" to AttributeValue.N(event.startDateMillis.toString())
                )
            }
            dynamoDbClient.putItem(request)
        }
    }

    suspend fun deleteMessMenu(mealType: String, dayOfWeek: String) {
        val request = DeleteItemRequest {
            tableName = "CampusFlow_MessMenu"
            key = mapOf(
                "mealType" to AttributeValue.S(mealType),
                "dayOfWeek" to AttributeValue.S(dayOfWeek)
            )
        }
        dynamoDbClient.deleteItem(request)
    }
}
