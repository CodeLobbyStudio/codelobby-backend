"""Run the Java lobby happy path against a local backend and its configured Runner."""

import argparse
import concurrent.futures
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid


def request(base_url, path, expected, *, token=None, body=None, post=False):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        headers["Content-Type"] = "application/json"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    method = "POST" if post or body is not None else "GET"
    req = urllib.request.Request(base_url + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=75) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status not in expected:
        reply = json.loads(raw) if raw else {}
        raise AssertionError(f"{method} {path}: expected {expected}, got {status} {reply.get('code', '')}")
    return status, json.loads(raw) if raw else None


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")
    seed = uuid.uuid4().hex
    demo_password = "LocalSmoke123!"

    def new_user(role):
        body = {"email": f"{seed}-{role}@example.test", "password": demo_password}
        request(base_url, "/api/auth/register", (201,), body=body)
        _, response = request(base_url, "/api/auth/login", (200,), body=body)
        return response["accessToken"]

    host = new_user("host")
    candidates = [new_user("participant"), new_user("waiting")]
    fixture = Path(__file__).resolve().parents[1] / "docs/examples/create-java-task.json"
    _, task = request(base_url, "/api/tasks", (201,), token=host, body=json.loads(fixture.read_text()))

    _, lobby = request(base_url, "/api/lobbies", (201,), token=host, body={
        "name": "Java flow smoke test", "taskIds": [task["id"]], "allowedLanguages": ["JAVA"],
        "maxParticipants": 2, "durationMinutes": 5,
    })
    lobby_path = f"/api/lobbies/{lobby['id']}"
    task_path = f"{lobby_path}/tasks/{task['id']}"
    join_body = {"code": " " + lobby["joinCode"].lower() + " "}

    def join(token):
        status, response = request(base_url, "/api/lobbies/join", (200, 409), token=token, body=join_body)
        return token, status, response

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        joins = list(executor.map(join, candidates))

    require(sorted(status for _, status, _ in joins) == [200, 409], "One participant must win the last seat")
    participant, _, joined = next(result for result in joins if result[1] == 200)
    outside, _, rejected = next(result for result in joins if result[1] == 409)
    require(joined["role"] == "PARTICIPANT" and rejected["code"] == "LOBBY_FULL", "Unexpected join response")
    require(join(participant)[1] == 200, "Repeating join must succeed")
    request(base_url, task_path + "/workspace", (409,), token=participant)
    request(base_url, lobby_path + "/start", (403,), token=participant, post=True)
    _, started = request(base_url, lobby_path + "/start", (200,), token=host, post=True)
    require(started["status"] == "ACTIVE" and len(started["members"]) == 2, "Invalid active lobby state")
    require(started["startedAt"] and started["endsAt"], "Session timestamps must be returned")
    request(base_url, task_path + "/workspace", (403,), token=outside)
    _, workspace = request(base_url, task_path + "/workspace?language=JAVA", (200,), token=participant)
    require(all(file["type"] in {"EDITABLE", "READ_ONLY"} for file in workspace["files"]),
            "The workspace must contain only editable and read-only sources")
    require(workspace["publicTests"] == [{"testId": "public-1"}], "Missing public test metadata")

    def run(files, expected):
        _, response = request(base_url, task_path + "/run", (200,), token=participant, body={
            "taskVersionId": workspace["taskVersionId"], "language": "JAVA", "files": files,
        })
        result = response["result"]
        require(result["status"] == expected, f"Expected {expected}, got {result['status']}")
        require(result["testResults"][0]["testId"] == "public-1", "Unexpected result test IDs")

    run([], "WRONG_ANSWER")
    run([{"path": "src/Solution.java", "content": (
        "public class Solution { public static int countVowels(String text) { "
        "return Helpers.count(text); } }"
    )}], "ACCEPTED")
    run([{"path": "src/Solution.java", "content": "public class Solution { broken }"}],
        "COMPILATION_ERROR")

    _, finished = request(base_url, lobby_path + "/finish", (200,), token=host, post=True)
    require(finished["status"] == "FINISHED", "Host must be able to finish the session")
    request(base_url, task_path + "/workspace", (409,), token=participant)
    print(f"Java lobby {lobby['id']}: last-seat race, session, workspace and three Run outcomes passed")


if __name__ == "__main__":
    main()
