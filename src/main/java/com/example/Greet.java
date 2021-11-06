package com.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Greet {
    
    private String name;
    private String period;


    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getPeriod() {
        return period;
    }
    public void setPeriod(String period) {
        this.period = period;
    }
}
