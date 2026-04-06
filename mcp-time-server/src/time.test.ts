import { getCurrentTimeISO } from './time.js';

describe('getCurrentTimeISO', () => {
  it('returns a string matching ISO 8601 with timezone offset', () => {
    const result = getCurrentTimeISO();
    // e.g. 2026-04-06T14:32:10.123+03:00 or 2026-04-06T11:00:00.000-05:00
    expect(result).toMatch(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2}$/
    );
  });

  it('returns a value that can be parsed back to a valid Date', () => {
    const result = getCurrentTimeISO();
    const parsed = new Date(result);
    expect(parsed.getTime()).not.toBeNaN();
  });
});
