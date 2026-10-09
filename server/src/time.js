const ZONE = 'Europe/London';

function zoneParts(date, timeZone) {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
  const bag = {};
  for (const part of dtf.formatToParts(date)) bag[part.type] = part.value;
  return {
    year: Number(bag.year),
    month: Number(bag.month),
    day: Number(bag.day),
    hour: bag.hour === '24' ? 0 : Number(bag.hour),
    minute: Number(bag.minute),
    second: Number(bag.second),
  };
}

function zoneOffsetMs(date, timeZone) {
  const parts = zoneParts(date, timeZone);
  const asUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
  return asUtc - date.getTime();
}

function zonedCivilToUtc(year, month, day, hour, minute, second, timeZone) {
  const wall = Date.UTC(year, month - 1, day, hour, minute, second);
  const first = new Date(wall);
  const offset = zoneOffsetMs(first, timeZone);
  let utc = new Date(wall - offset);
  const offset2 = zoneOffsetMs(utc, timeZone);
  if (offset2 !== offset) utc = new Date(wall - offset2);
  return utc;
}

function addCalendarDay(year, month, day) {
  const next = new Date(Date.UTC(year, month - 1, day));
  next.setUTCDate(next.getUTCDate() + 1);
  return {
    year: next.getUTCFullYear(),
    month: next.getUTCMonth() + 1,
    day: next.getUTCDate(),
  };
}

/** Next 01:00 Europe/London. At exactly 01:00, roll to the following day. */
export function nextLondonReset(now = new Date()) {
  const parts = zoneParts(now, ZONE);
  let { year, month, day } = parts;
  if (parts.hour >= 1) {
    const rolled = addCalendarDay(year, month, day);
    year = rolled.year;
    month = rolled.month;
    day = rolled.day;
  }
  let target = zonedCivilToUtc(year, month, day, 1, 0, 0, ZONE);
  if (target.getTime() <= now.getTime()) {
    const rolled = addCalendarDay(year, month, day);
    target = zonedCivilToUtc(rolled.year, rolled.month, rolled.day, 1, 0, 0, ZONE);
  }
  return target;
}

export function shopRefresh(now = new Date()) {
  const resetsAt = nextLondonReset(now);
  const seconds = Math.max(1, Math.floor((resetsAt.getTime() - now.getTime()) / 1000));
  return {
    seconds,
    resetsAt: resetsAt.toISOString(),
    timezone: ZONE,
    label: '01:00',
  };
}
