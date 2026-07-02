// QR check-in payload format shared by profile display and scanner
export const QR_CHECKIN_PREFIX = 'smartground://checkin/'

export function buildCheckinPayload(token) {
  return `${QR_CHECKIN_PREFIX}${token}`
}

// Returns the token, or null when the payload is not a Smart Ground check-in code
export function parseCheckinPayload(text) {
  if (typeof text !== 'string' || !text.startsWith(QR_CHECKIN_PREFIX)) return null
  const token = text.slice(QR_CHECKIN_PREFIX.length).trim()
  return token.length > 0 ? token : null
}
