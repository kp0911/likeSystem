import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 500 },
    { duration: '3m', target: 500 },
    { duration: '1m', target: 0 },
  ],
};

export default function () {
  const url = 'http://localhost:8080/api/v1/like/sync';
  
  const payload = JSON.stringify({
    videoId: 1,
    userId: `user-${__VU}-${__ITER}`
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  sleep(0.1);
}
