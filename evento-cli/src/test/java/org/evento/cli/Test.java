package org.evento.cli;

public class Test {

    public static void main(String[] args) throws Throwable {
        PublishBundle.main(new String[]{
                "C:\\Users\\ggalazzo\\workspace\\personal\\eventrails\\evento-demo\\evento-demo-agent",
                "http://localhost:3000",
                "https://github.com/EventoFramework/evento-framework/blob/main/evento-demo/evento-demo-agent",
                "eyJraWQiOiJldmVudG8tc2VydmVyLWF1dGgta2V5IiwidHlwIjoiSldUIiwiYWxnIjoiSFMyNTYifQ.eyJjbGllbnRJZCI6ImRlcGxveUtleSIsInJvbGUiOlsiUk9MRV9QVUJMSVNIIl0sIm5iZiI6MTY5NTU3NjU4MywiaXNzIjoiZXZlbnRvLXNlcnZlciIsImV4cCI6MTcyNzExMjU4NCwiaWF0IjoxNjk1NTc2NTg0LCJqdGkiOiIxYmVjMjY0MC02YzgwLTRkZDctYjc1NC1hMDNmMTIzZjZmNTcifQ.9pY37cPqa55kAzWG8OlqPvObQ6en-f0D45HWRqzZU3E"
        } );
    }
}
