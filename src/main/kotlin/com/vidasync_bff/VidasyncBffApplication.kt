package com.vidasync_bff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@SpringBootApplication
@EnableFeignClients
class VidasyncBffApplication

fun main(args: Array<String>) {
	runApplication<VidasyncBffApplication>(*args)
}
