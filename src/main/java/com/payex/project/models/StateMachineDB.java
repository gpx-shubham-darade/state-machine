package com.payex.project.models;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldNameConstants
public class StateMachineDB {
    String _id;
    String stateMachineName;
    List<String> states;
    List<String> events;
    Map<String, Map<String, String>> transitions;
    int partition;
}
