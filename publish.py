import requests
import os

def get_gradle_properties():
    properties = {}
    gradle_properties_path = os.path.expanduser("~/.gradle/gradle.properties")

    if not os.path.exists(gradle_properties_path):
        print("No gradle.properties file found in ~/.gradle/")
        return properties

    with open(gradle_properties_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                key, value = line.split('=', 1)
                properties[key.strip()] = value.strip()
    return properties

# Example usage
if __name__ == "__main__":
    props = get_gradle_properties()

    resp = requests.get("https://ossrh-staging-api.central.sonatype.com/manual/search/repositories",
                 auth=(props['mavenCentralUsername'], props['mavenCentralPassword']))

    print(resp.text)

    resp = requests.post("https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.eventoframework?publishing_type=automatic",
                        auth=(props['mavenCentralUsername'], props['mavenCentralPassword']))

    print(resp.text)

    resp = requests.get("https://ossrh-staging-api.central.sonatype.com/manual/search/repositories",
                        auth=(props['mavenCentralUsername'], props['mavenCentralPassword']))

    print(resp.text)