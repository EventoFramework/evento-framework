package com.evento.demo.agent.agents;

public record Report(
		String uid,
		long createTime,
		long meanUpdateTime,
		long deleteTime, boolean success) {

	@Override
	public String toString() {
		return Report.this.uid + "\t" + Report.this.createTime + "\t" + Report.this.meanUpdateTime + "\t" + Report.this.deleteTime + "\t" + Report.this.success;
	}
}