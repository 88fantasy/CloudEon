package com.data.udh.controller.request;

import lombok.Data;

import java.util.List;

@Data
public class OpsServiceRoleRequest {
    private Integer serviceInstanceId;
    List<Integer> roleInstanceIds;
}