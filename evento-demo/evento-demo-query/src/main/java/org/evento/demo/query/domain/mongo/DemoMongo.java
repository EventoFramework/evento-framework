package org.evento.demo.query.domain.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.evento.demo.api.view.DemoRichView;
import org.evento.demo.api.view.DemoView;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DemoMongo {
    private String id;
    private String name;
    private Long value;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public DemoView toDemoView() {
        var view = new DemoView();
        view.setDemoId(this.getId());
        view.setName(this.getName());
        view.setValue(this.getValue());
        return view;
    }

    public DemoRichView toDemoRichView() {
        var view = new DemoRichView();
        view.setDemoId(this.getId());
        view.setName(this.getName());
        view.setValue(this.getValue());
        view.setCreatedAt(this.getCreatedAt());
        view.setUpdatedAt(this.getUpdatedAt());
        return view;
    }
}
