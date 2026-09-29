package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VirtualCardOrderRequest {
    @NotBlank
    @Size(max = 26)
    private String embossName;

    public String getEmbossName() { return embossName; }
    public void setEmbossName(String embossName) { this.embossName = embossName; }
}
