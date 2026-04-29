package com.rinoimob.domain.dto;

public record LeadStatsResponse(
    long total,
    long newLeads,
    long contacted,
    long qualified,
    long won,
    long lost,
    long thisWeek,
    double conversionRate   // won / (won + lost) * 100, 0 if both are 0
) {}
