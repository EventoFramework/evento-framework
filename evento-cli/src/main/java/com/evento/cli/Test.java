package com.evento.cli;

public class Test {

    public static void main(String[] args) throws Throwable {
        PublishBundle.main(new String[]{
                "C:\\Users\\ggalazzo\\workspace\\iris_5\\iris-server\\iris-gateway-api",
                "http://localhost:3000",
                "https://bitbucket.org/glccloud01/iris-core/src/master/iris-server/iris-gateway-api",
                "lines-",
                "20-preview",
                "false",
                "eyJraWQiOiJldmVudG8tc2VydmVyLWF1dGgta2V5IiwiYWxnIjoiSFMyNTYiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJldmVudG8tc2VydmVyIiwiY2xpZW50SWQiOiJkZXBsb3lLZXkiLCJyb2xlIjpbIlJPTEVfUFVCTElTSCJdLCJpYXQiOjE3MDk2NjM1NTYsImV4cCI6MTc0MTE5OTU1NiwianRpIjoiNmJlMGM5YTQtZDQ0Ni00NzE1LWJlNzAtY2NkMzgxMmExNWI2IiwibmJmIjoxNzA5NjYzNTU1fQ.MIGuorOyP__sPwfWnZyKhuhIhvjaRj-Kojl94hTzc9c"
        } );
    }
}
