package com.evento.demo.query.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.evento.demo.api.view.DemoRichView;
import com.evento.demo.api.view.DemoView;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Demo {
    @Id
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
        view.setCreatedAt(this.getCreatedAt().toEpochMilli());
        if (this.getUpdatedAt() != null)
            view.setUpdatedAt(this.getUpdatedAt().toEpochMilli());
        if (this.deletedAt != null)
            view.setDeletedAt(this.getDeletedAt().toEpochMilli());
        return view;
    }
}
