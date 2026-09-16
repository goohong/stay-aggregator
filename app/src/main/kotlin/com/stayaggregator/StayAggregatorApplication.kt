package com.stayaggregator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// 목록 동기화를 기동 시와 주기마다 돌린다 (ADR-0013, ADR-0016)
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class StayAggregatorApplication

fun main(args: Array<String>) {
    runApplication<StayAggregatorApplication>(*args)
}
