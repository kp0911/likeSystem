import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: 500,
      iterations: 1,
      maxDuration: '30s',
      gracefulStop: '10s',
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    videoId: 1,
  });

  const res = http.post('http://localhost:8080/api/v1/like/sync', payload, {
    headers: {
      'Content-Type': 'application/json',
    },
  });

  check(res, {
    'status is 200': (response) => response.status === 200,
  });
}
