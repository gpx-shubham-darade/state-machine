package com.payex.project.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldNameConstants
public class ConfigLoader {
    private String mongoUri;
    private String mongoDatabase;
    private int serverPort;
    private String redisHost;
    private int redisPort;
    private String kafkaBootstrapServers;
    private String kafkaGroupId;
    private String kafkaTopic;

    public static ConfigLoader loadConfig() {
        try (InputStream inputStream = Files.newInputStream(Paths.get("src/main/resources/application.yml"))) {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(inputStream);

            Map<String, Object> mongodb = (Map<String, Object>) yamlMap.get("mongodb");
            Map<String, Object> server = (Map<String, Object>) yamlMap.get("server");
            Map<String, Object> redis = (Map<String, Object>) yamlMap.get("redis");
            Map<String, Object> kafka = (Map<String, Object>) yamlMap.get("kafka");

            return ConfigLoader.builder()
                    .mongoUri((String) mongodb.get("uri"))
                    .mongoDatabase((String) mongodb.get("database"))
                    .serverPort((Integer) server.get("port"))
                    .redisHost((String) redis.get("host"))
                    .redisPort((Integer) redis.get("port"))
                    .kafkaBootstrapServers((String) kafka.get("bootstrapServers"))
                    .kafkaGroupId((String) kafka.get("groupId"))
                    .kafkaTopic((String) kafka.get("topic"))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + e.getMessage(), e);
        }
    }
}
