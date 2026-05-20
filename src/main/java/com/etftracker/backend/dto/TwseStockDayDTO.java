package com.etftracker.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TwseStockDayDTO {

    @JsonProperty("Code")
    private String code;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("ClosingPrice")
    private String closingPrice;
}
