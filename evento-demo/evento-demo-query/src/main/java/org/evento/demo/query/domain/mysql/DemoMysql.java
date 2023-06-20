package org.evento.demo.query.domain.mysql;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.evento.demo.api.view.DemoRichView;
import org.evento.demo.api.view.DemoView;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.Instant;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DemoMysql {
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
		view.setUpdatedAt(this.getUpdatedAt().toEpochMilli());
		view.setDeletedAt(this.getDeletedAt().toEpochMilli());
		return view;
	}
}
