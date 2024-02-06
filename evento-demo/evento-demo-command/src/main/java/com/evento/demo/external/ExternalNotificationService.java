package com.evento.demo.external;


import com.evento.demo.api.utils.Utils;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ExternalNotificationService {
	public String send(String body) {
		Utils.doWork(1100);
		return UUID.randomUUID().toString();
	}
}
