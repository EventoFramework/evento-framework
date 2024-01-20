import sys
import json
import subprocess
import time

print(sys.argv[1])
bundle = json.loads(sys.argv[1])
token = sys.argv[2]

print(bundle)

artifact = bundle["artifactCoordinates"]

print(artifact)

cmd = [
    "docker",
    "run",
    "--name", bundle["id"] + "-" + str(round(time.time() * 1000)),
    "-d",
    # "--rm",
    "-e", "APP_JAR_URL=http://host.docker.internal:3000/asset/bundle/" + bundle["id"] + "?token=" + token]

for k, v in bundle["environment"]:
    cmd.append("-e")
    cmd.append(k + "=" + v)
cmd.append("evento-bundle-container")
print("Command: " + " ".join(cmd))
subprocess.run(cmd)
