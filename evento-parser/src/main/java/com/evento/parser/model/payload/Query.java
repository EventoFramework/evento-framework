package com.evento.parser.model.payload;

/**
 * The Query class represents a query object.
 * It extends the Payload class and implements Serializable for object serialization and deserialization.
 */
public class Query extends Payload {
	private QueryReturnType returnType;

	/**
	 * Constructs a new Query object with the specified name and return type.
	 *
	 * @param name the name of the query
	 * @param returnType the return type of the query
	 */
	public Query(String name, QueryReturnType returnType) {
		super(name);
		this.returnType = returnType;
	}

	/**
	 *
	 */
	public Query() {
		super();
	}

	/**
	 * Retrieves the return type of the query.
	 *
	 * @return the return type of the query
	 */
	public QueryReturnType getReturnType() {
		return returnType;
	}


	/**
	 * Sets the return type of the query.
	 *
	 * @param returnType the return type to be set for the query
	 */
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
		if (!(o instanceof Query query)) return false;

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
