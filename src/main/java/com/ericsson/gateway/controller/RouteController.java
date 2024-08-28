/*
 * Copyright Ericsson 2020
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.gateway.controller;

import com.ericsson.gateway.model.GatewayFilterDefinition;
import com.ericsson.gateway.model.GatewayPredicateDefinition;
import com.ericsson.gateway.model.GatewayRouteDefinition;
import com.ericsson.gateway.service.DynamicRouteServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * gateway
 * POST /gateway/routes/{id_route_to_create}, JSON
 * {
 *   "id": "first_route",
 *   "predicates": [{
 *     "name": "Path",
 *     "args": {"_genkey_0":"/first"}
 *   }],
 *   "filters": [],
 *   "uri": "http://www.uri-destination.org",
 *   "order": 0
 * }]
 * DELETE /gateway/routes/{id_route_to_delete}
 *
 */
@RestController
@RequestMapping("/route")
public class RouteController {

    @Autowired
    private DynamicRouteServiceImpl dynamicRouteService;

    /**
     * Add a route definition
     *
     * @param gatewayRouteDefinition
     * @return
     */
    @PostMapping("/add")
    public Boolean add(@RequestBody GatewayRouteDefinition gatewayRouteDefinition) {
        try {
            RouteDefinition definition = assembleRouteDefinition(gatewayRouteDefinition);
            dynamicRouteService.add(definition);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Boolean.FALSE;
    }

    /**
     * Delete a route definition
     *
     * @param id
     * @return
     */
    @GetMapping("/delete/{id}")
    public Boolean delete(@PathVariable String id) {
        dynamicRouteService.delete(id);
        return true;
    }

    /**
     * Update a route definition
     *
     * @param gatewayRouteDefinition
     * @return
     */
    @PostMapping("/update")
    public Boolean update(@RequestBody GatewayRouteDefinition gatewayRouteDefinition) {
        RouteDefinition definition = assembleRouteDefinition(gatewayRouteDefinition);
        dynamicRouteService.update(definition);
        return true;
    }

    private RouteDefinition assembleRouteDefinition(GatewayRouteDefinition gwdefinition) {
        RouteDefinition definition = new RouteDefinition();
        List<PredicateDefinition> pdList = new ArrayList<>();
        definition.setId(gwdefinition.getId());
        List<GatewayPredicateDefinition> gatewayPredicateDefinitionList = gwdefinition.getPredicates();
        for (GatewayPredicateDefinition gpDefinition : gatewayPredicateDefinitionList) {
            PredicateDefinition predicate = new PredicateDefinition();
            predicate.setArgs(gpDefinition.getArgs());
            predicate.setName(gpDefinition.getName());
            pdList.add(predicate);
        }

        List<GatewayFilterDefinition> gatewayFilterDefinitions = gwdefinition.getFilters();
        List<FilterDefinition> filterList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(gatewayFilterDefinitions)) {
            for (GatewayFilterDefinition gatewayFilterDefinition : gatewayFilterDefinitions) {
                FilterDefinition filterDefinition = new FilterDefinition();
                filterDefinition.setName(gatewayFilterDefinition.getName());
                filterDefinition.setArgs(gatewayFilterDefinition.getArgs());
                filterList.add(filterDefinition);
            }
        }
        definition.setPredicates(pdList);
        definition.setFilters(filterList);
        URI uri = UriComponentsBuilder.fromHttpUrl(gwdefinition.getUri()).build().toUri();
        definition.setUri(uri);
        return definition;
    }
}