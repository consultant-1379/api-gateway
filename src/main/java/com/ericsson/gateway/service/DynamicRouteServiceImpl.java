/*
 * Copyright Ericsson 2020
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.gateway.service;

import com.ericsson.gateway.controller.RouteController;
import com.ericsson.gateway.dao.NacosRouteDefinitionRepository;
import com.ericsson.gateway.model.GatewayRouteDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Dynamic Update Routing Gateway service
 * 1)Implement an event push interface provided by Spring ApplicationEventPublisherAware
 * 2)Provides the basic method of dynamic routing by getting a bean to manipulate the methods of this class.This class provides new routes, updates routes, deletes routes, and then implements the functions of publishing.
 */
@Service
public class DynamicRouteServiceImpl implements ApplicationEventPublisherAware {
    @Autowired
    NacosRouteDefinitionRepository routeDefinitionRepository;

    /**
     * Publish Events
     */
    @Autowired
    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
        NacosRouteDefinitionRepository.getRoutesFromEtcd().forEach(routeDefinition
                -> Mono.just(routeDefinition).flatMap( r -> {return Mono.empty();}).subscribe());
        this.publisher.publishEvent(new RefreshRoutesEvent(this));
    }

    /**
     * Add Routing
     * @param definition
     * @return
     */
    public String add(RouteDefinition definition) {
        routeDefinitionRepository.save(Mono.just(definition)).subscribe();
        this.publisher.publishEvent(new RefreshRoutesEvent(this));

        return "success";
    }

    /**
     * Update Route
     * @param definition
     * @return
     */
    public String update(RouteDefinition definition) {
        try {
            routeDefinitionRepository.delete(Mono.just(definition.getId()));
        } catch (Exception e) {
            return "update fail,not find route  routeId: "+definition.getId();
        }
        try {
            routeDefinitionRepository.save(Mono.just(definition)).subscribe();
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
            return "success";
        } catch (Exception e) {
            return "update route  fail";
        }
    }

    /**
     * Delete Route
     * @param id
     * @return
     */
    public String delete(String id) {
        try {
            this.routeDefinitionRepository.delete(Mono.just(id));
            return "delete success";
        } catch (Exception e) {
            e.printStackTrace();
            return "delete fail";
        }
    }

    /**
     * Refresh Routing
     * @return
     */
    public String refresh(){
        try {
            routeDefinitionRepository.refresh();
            return "refresh success";
        } catch (Exception e) {
            e.printStackTrace();
            return "refresh fail";
        }
    }
}