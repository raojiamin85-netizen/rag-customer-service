package com.example;

import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDate;

public class DateCalculator {

    @Tool("计算指定天数后的具体日期")
    public String date(Integer days) {
        if (days == null) {
            return LocalDate.now().toString();
        }
        return LocalDate.now().plusDays(days).toString();
    }
}
