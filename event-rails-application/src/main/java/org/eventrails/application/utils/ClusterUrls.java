package org.eventrails.application.utils;

import java.util.Arrays;

public class ClusterUrls {
	private final String[] urls;
	private int index = 0;

	public ClusterUrls(String serverUrls) {
		this.urls = serverUrls.split(";");
	}

	public String pickClusterUrl(){
		var url =  urls[index++];
		index = index % urls.length;
		return url;
	}
}
