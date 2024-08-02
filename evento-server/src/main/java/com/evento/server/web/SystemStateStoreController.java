package com.evento.server.web;

import com.evento.server.es.EventStore;
import com.evento.server.es.snapshot.Snapshot;
import com.evento.server.web.dto.EventDTO;
import com.evento.server.web.dto.SnapshotDTO;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;

@RestController()
@RequestMapping("api/system-state-store")
public class SystemStateStoreController {

    private final EventStore eventStore;

    public SystemStateStoreController(EventStore eventStore) {
        this.eventStore = eventStore;
    }


    @GetMapping("/event")
    public Page<EventDTO> searchEvents(
            @RequestParam(required = false) String aggregateIdentifier,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) Integer eventSequenceNumber,
            @RequestParam(required = false) Timestamp createdAtFrom,
            @RequestParam(required = false) Timestamp createdAtTo,
            @RequestParam(required = false) String contentQuery,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,
            @RequestParam(defaultValue = "eventSequenceNumber") String sortProperty
    ) throws BadRequestException {
        if(size > 1000){
            throw new BadRequestException("size must be less than 1000");
        }
        return eventStore.searchEvents(
                aggregateIdentifier,
                eventName,
                context,
                eventSequenceNumber,
                createdAtFrom,
                createdAtTo,
                contentQuery,
                page,
                size,
                sortDirection,
                sortProperty
        ).map(EventDTO::new);
    }
    @GetMapping("/snapshot")
    public Page<SnapshotDTO> searchSnapshots(
            @RequestParam(required = false) String aggregateIdentifier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,
            @RequestParam(defaultValue = "eventSequenceNumber") String sortProperty
    ) throws BadRequestException {
        if(size > 1000){
            throw new BadRequestException("size must be less than 1000");
        }
        return eventStore.searchSnapshots(
                aggregateIdentifier,
                page,
                size,
                sortDirection,
                sortProperty
        ).map(SnapshotDTO::new);
    }

}
