#!/usr/bin/env python3
import json
import subprocess
import time
from typing import Tuple, List

MISTRAL_API_KEY = "zsEegAJFadHH4uooe2lW0HVNmy1rpqGT"
MISTRAL_MODEL = "mistral-large-latest"
MISTRAL_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"


def now_ms() -> int:
    return int(time.time() * 1000)


def curl_chat(payload: dict, stream: bool) -> Tuple[int, int, int]:
    """
    Returns: (http_code, request_started_ms, last_token_ms_or_response_end_ms)
    For non-stream requests, 3rd value is response-end timestamp.
    """
    request_started = now_ms()
    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        MISTRAL_ENDPOINT,
        "-H",
        "Content-Type: application/json",
        "-H",
        f"Authorization: Bearer {MISTRAL_API_KEY}",
        "--data-binary",
        json.dumps(payload),
        "-w",
        "\nHTTP_STATUS:%{http_code}\n",
    ]
    if stream:
        cmd.insert(1, "-N")

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    last_token_ms = request_started
    http_code = 0
    assert proc.stdout is not None
    for line in proc.stdout:
        line = line.rstrip("\n")
        if line.startswith("data:"):
            data = line[5:].strip()
            if data and data != "[DONE]":
                last_token_ms = now_ms()
        elif line.startswith("HTTP_STATUS:"):
            try:
                http_code = int(line.split(":", 1)[1].strip())
            except ValueError:
                http_code = 0

    exit_code = proc.wait()
    if exit_code != 0:
        raise RuntimeError(f"curl failed with exit code {exit_code}")

    if not stream:
        last_token_ms = now_ms()
    return http_code, request_started, last_token_ms


def sleep_until(target_ms: int) -> None:
    remaining = target_ms - now_ms()
    if remaining > 0:
        time.sleep(remaining / 1000.0)


def probe_last_token_mode(delays: List[int]) -> None:
    print("=== PROBE: ab_letztem_token ===")
    min_success = None
    for delay in delays:
        stream_payload = {
            "model": MISTRAL_MODEL,
            "messages": [{"role": "user", "content": "Sag nur OK."}],
            "max_tokens": 32,
            "stream": True,
        }
        code, _, last_token = curl_chat(stream_payload, stream=True)
        if code != 200:
            print(f"baseline_stream_failed http={code}")
            continue

        sleep_until(last_token + delay)
        probe_payload = {
            "model": MISTRAL_MODEL,
            "messages": [{"role": "user", "content": "OK?"}],
            "max_tokens": 1,
            "stream": False,
        }
        probe_code, _, _ = curl_chat(probe_payload, stream=False)
        print(f"delay={delay}ms http={probe_code}")
        if min_success is None and probe_code == 200:
            min_success = delay
    print(f"min_success_delay_ms={min_success}")
    print()


def probe_request_start_mode(delays: List[int]) -> None:
    print("=== PROBE: ab_request_start ===")
    min_success = None
    for delay in delays:
        baseline_payload = {
            "model": MISTRAL_MODEL,
            "messages": [{"role": "user", "content": "Sag nur OK."}],
            "max_tokens": 32,
            "stream": True,
        }
        request_started = now_ms()
        baseline_cmd = [
            "curl",
            "-sS",
            "-N",
            "-X",
            "POST",
            MISTRAL_ENDPOINT,
            "-H",
            "Content-Type: application/json",
            "-H",
            f"Authorization: Bearer {MISTRAL_API_KEY}",
            "--data-binary",
            json.dumps(baseline_payload),
            "-w",
            "\nHTTP_STATUS:%{http_code}\n",
        ]
        baseline_proc = subprocess.Popen(
            baseline_cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        sleep_until(request_started + delay)
        probe_payload = {
            "model": MISTRAL_MODEL,
            "messages": [{"role": "user", "content": "OK?"}],
            "max_tokens": 1,
            "stream": False,
        }
        probe_code, _, _ = curl_chat(probe_payload, stream=False)
        print(f"delay={delay}ms http={probe_code}")
        if min_success is None and probe_code == 200:
            min_success = delay

        baseline_output, _ = baseline_proc.communicate()
        baseline_status = 0
        for line in baseline_output.splitlines():
            if line.startswith("HTTP_STATUS:"):
                try:
                    baseline_status = int(line.split(":", 1)[1].strip())
                except ValueError:
                    baseline_status = 0
        if baseline_status != 200:
            print(f"baseline_stream_failed http={baseline_status}")
    print(f"min_success_delay_ms={min_success}")
    print()


if __name__ == "__main__":
    step_delays = list(range(100, 3001, 100))
    probe_last_token_mode(step_delays)
    probe_request_start_mode(step_delays)
