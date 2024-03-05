package com.evento.cli;

public class Test {

    public static void main(String[] args) throws Throwable {
        PublishBundle.main(new String[]{
                "C:\\Users\\ggalazzo\\workspace\\personal\\eventrails\\evento-demo\\evento-demo-agent",
                "http://localhost:3000",
                "https://github.com/EventoFramework/evento-framework/blob/main/evento-demo/evento-demo-agent",
                "eyJraWQiOiJldmVudG8tc2VydmVyLWF1dGgta2V5IiwiYWxnIjoiSFMyNTYiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJldmVudG8tc2VydmVyIiwiY2xpZW50SWQiOiJkZXBsb3lLZXkiLCJyb2xlIjpbIlJPTEVfUFVCTElTSCJdLCJpYXQiOjE3MDk2NjM1NTYsImV4cCI6MTc0MTE5OTU1NiwianRpIjoiNmJlMGM5YTQtZDQ0Ni00NzE1LWJlNzAtY2NkMzgxMmExNWI2IiwibmJmIjoxNzA5NjYzNTU1fQ.MIGuorOyP__sPwfWnZyKhuhIhvjaRj-Kojl94hTzc9c"
        } );
    }
}
