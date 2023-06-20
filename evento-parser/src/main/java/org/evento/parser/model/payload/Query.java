package org.evento.parser.model.payload;

public class Query extends Payload {
	private QueryReturnType returnType;

	public Query(String name, QueryReturnType returnType) {
		super(name);
		this.returnType = returnType;
	}

	public Query() {
		super();
	}

	public QueryReturnType getReturnType() {
		return returnType;
	}

	public void setReturnType(QueryReturnType returnType) {
		this.returnType = returnType;
	}

	@Override
	public String toString() {
		return getName() + ":" + returnType;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Query)) return false;

		Query query = (Query) o;

		if (getReturnType() != null ? !getReturnType().equals(query.getReturnType()) : query.getReturnType() != null)
			return false;
		return getName() != null ? getName().equals(query.getName()) : query.getName() == null;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (getReturnType() != null ? getReturnType().hashCode() : 0);
		result = 31 * result + (getName() != null ? getName().hashCode() : 0);
		return result;
	}
}
