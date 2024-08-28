package com.ericsson.gateway.reverseproxy;

//import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
//import org.springframework.context.*;
//import org.springframework.stereotype.Component;
//
//
///**
// * A gateway component responsible for publishing route changes.
// *
// * @author          boto
// * Creation Date    25th June 2018
// */
//@Component
//public class GatewayRoutesRefresher implements ApplicationEventPublisherAware {
//
//    private ApplicationEventPublisher publisher;
//
//    @Override
//    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
//        publisher = applicationEventPublisher;
//    }
//
//    public void refreshRoutes() {
//        publisher.publishEvent(new RefreshRoutesEvent(this));
//    }
//}