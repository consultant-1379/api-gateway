package com.ericsson.gateway.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.synchronizedMap;

/**
 * @author: lpb
 * @create: 2020-07-24 11:22
 */
@Repository
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    private final Map<String, RouteDefinition> routes = synchronizedMap(new LinkedHashMap<String, RouteDefinition>());
    Client client = Client.builder().endpoints("http://localhost:2379").build();
    KV kvClient = client.getKVClient();

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        System.out.println("SAVING ROUTE IN DB");
        ObjectMapper Obj = new ObjectMapper();
        return route.flatMap(r -> {
            try {
                kvClient.put(ByteSequence.from(r.getId(), UTF_8), ByteSequence.from(Obj.writeValueAsString(r), UTF_8));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return Mono.empty();
        });
    }


    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            if (routes.containsKey(id)) {
                routes.remove(id);
                try {
                    kvClient.delete(ByteSequence.from(ByteString.copyFromUtf8(id))).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
                return Mono.empty();
            }
            return Mono.defer(() -> Mono.error(new NotFoundException("RouteDefinition not found: " + routeId)));
        });
    }

    public boolean refresh() {
        routes.clear();
        return true;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(getRoutesFromEtcd());
    }

    public static List<RouteDefinition> getRoutesFromEtcd() {
        Client client = Client.builder().endpoints("http://localhost:2379").build();
        KV kvClient = client.getKVClient();

        GetResponse getResponse = null;
        try {
            getResponse =
                    kvClient.get(ByteSequence.from(ByteString.copyFromUtf8("route")), GetOption.newBuilder()
                            .withPrefix(ByteSequence.from(ByteString.copyFromUtf8("route"))).build()).get();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        ObjectMapper mapper = new ObjectMapper();
        List<RouteDefinition> routes = new ArrayList<>();
        for (KeyValue kv : getResponse.getKvs()) {
            String s = kv.getValue().toString(UTF_8);
            try {
                RouteDefinition definition = mapper.readValue(s, RouteDefinition.class);
                routes.add(definition);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return routes;
    }
}