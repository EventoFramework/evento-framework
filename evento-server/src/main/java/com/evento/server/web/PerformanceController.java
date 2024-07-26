package com.evento.server.web;

import com.evento.server.service.performance.model.AggregationFunction;
import com.evento.server.service.performance.PerformanceStoreService;
import com.evento.server.service.performance.model.PerformancePoint;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;

@RestController()
@RequestMapping("api/performance")
public class PerformanceController {

    private final PerformanceStoreService performanceStoreService;

    public PerformanceController(PerformanceStoreService performanceStoreService) {
        this.performanceStoreService = performanceStoreService;
    }

    @GetMapping("/component")
    @Secured("ROLE_WEB")
    public Map<String, Collection<PerformancePoint>> getComponentPerformances(
            String bundleId,
            String componentId,
            @RequestParam(defaultValue = "AVG") AggregationFunction serviceTimeAggregationFunction,
            @RequestParam(defaultValue = "60") Integer interval,
            ZonedDateTime from,
            ZonedDateTime to
    ){
        return performanceStoreService.getComponentPerformance(
                bundleId,
                componentId,
                serviceTimeAggregationFunction,
                interval,
                from,
                to
        );
    }
}
