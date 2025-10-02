package com.evento.common.utils;

import java.sql.Connection;

public interface ConnectionFactory {
    public Connection getConnection() throws Throwable;
}
