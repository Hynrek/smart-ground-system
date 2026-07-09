#!/bin/sh
# Creates the two dynsec roles the mqtt-tls-client-auth plan calls for
# (`backend`, `smartbox`) against a RUNNING mosquitto broker, plus the
# backend's own dynsec client login (Task B — the well-known `backend`
# username the backend itself authenticates as; per-SmartBox client
# creation/deletion is a separate, later task and is NOT done here).
#
# This script is NOT run automatically by docker-compose — dynsec role/ACL
# management happens over MQTT ($CONTROL/dynamic-security/#), which needs a
# live broker and an authenticated admin session, so it can't run at
# container build/start time the way cert generation can. Run it once,
# manually, after `docker compose up -d mosquitto` and after you've
# retrieved the initial admin password (see README.md "Dynsec bootstrap").
#
# Usage (from a machine with mosquitto_ctrl, e.g. inside the container):
#   docker compose exec mosquitto sh /mosquitto/config/dynsec-init.sh <admin-password> <backend-client-password>
# or, if mosquitto_ctrl is installed on the host and 8883 is reachable:
#   ./dynsec-init.sh <admin-password> <backend-client-password> --cafile /path/to/ca.crt -p 8883
#
# <backend-client-password> MUST be the same value the backend itself uses to
# log in (MQTT_PASSWORD / mqtt.password, see application-docker.properties) —
# see README.md's "Dynsec bootstrap" for the MQTT_BACKEND_PASSWORD <->
# MQTT_PASSWORD pairing this depends on.
#
# ACLs implement the plan's two roles:
#   backend  — read/write smartboxes/#, read/write $CONTROL/# (dynsec admin)
#   smartbox — write-only smartboxes/discovery; read/write scoped to its own
#              smartboxes/<mac>/... tree via the %u (username) substitution
#              pattern — the plan sets username = MAC per SmartBox.
set -eu

ADMIN_PASSWORD="${1:?Usage: dynsec-init.sh <admin-password> <backend-client-password> [extra mosquitto_ctrl options...]}"
BACKEND_PASSWORD="${2:?Usage: dynsec-init.sh <admin-password> <backend-client-password> [extra mosquitto_ctrl options...]}"
shift 2

# Defaults suit running this from inside the mosquitto container, talking to
# itself over the internal plaintext listener. Override via extra args (e.g.
# `-p 8883 --cafile /mosquitto/config/certs/ca.crt --cert ... ` from outside).
CTRL_OPTS="-h 127.0.0.1 -p 1883 -u admin -P $ADMIN_PASSWORD $*"

# RECOVERY: this script is NOT idempotent (relies on `set -eu` to abort on the
# first error, e.g. a transient mosquitto_ctrl failure partway through a role's
# ACL list). A rerun after a partial failure will immediately fail at
# `createRole` with "role already exists" and leave that role half-configured.
# To recover: manually delete the half-built role(s)/client before rerunning:
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteRole backend
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteRole smartbox
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteClient backend        # if already created
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteClient <username>     # any other client created
# No automatic retry/idempotency is implemented here by design — this is a
# one-shot bootstrap script, not a reconciler.

echo "== creating role: backend =="
mosquitto_ctrl $CTRL_OPTS dynsec createRole backend
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientSend    "smartboxes/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientReceive "smartboxes/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend subscribePattern     "smartboxes/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientSend    '$CONTROL/#' allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientReceive '$CONTROL/#' allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend subscribePattern     '$CONTROL/#' allow 10

echo "== creating role: smartbox =="
mosquitto_ctrl $CTRL_OPTS dynsec createRole smartbox
# Every SmartBox may announce itself on the shared discovery topic...
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientSend "smartboxes/discovery" allow 10
# ...but only read/write its OWN device subtree, keyed by username (= MAC).
# SECURITY: the "%u" below is substituted with the connecting client's dynsec
# username. This is only a safe per-device boundary because dynsec usernames
# are admin-assigned here (via `createClient`, not client-supplied). If a
# future provisioning step lets a device's username be attacker-influenced or
# unvalidated, a username containing MQTT wildcard characters ("+" or "#")
# would turn "smartboxes/%u/#" into "smartboxes/+/#" or "smartboxes/#", defeating the
# per-device isolation this ACL exists to enforce. Whoever implements the
# backend's device-provisioning/user-lifecycle code MUST validate that
# smartbox-role usernames are MAC-address-formatted (or otherwise free of
# "+"/"#"/wildcard characters) before calling `createClient`.
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientSend    "smartboxes/%u/#" allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientReceive "smartboxes/%u/#" allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox subscribePattern     "smartboxes/%u/#" allow 10

echo "== creating client: backend (the backend's own dynsec login) =="
# Non-interactive path: `createClient <user>` (no -p, prompts interactively
# otherwise — piping a password to that prompt did not work reliably in a
# non-tty `docker exec`, see Task A's report) then `setClientPassword`.
mosquitto_ctrl $CTRL_OPTS dynsec createClient backend
mosquitto_ctrl $CTRL_OPTS dynsec setClientPassword backend "$BACKEND_PASSWORD"
# `addClientRole` is known to print "Error: Internal error." on this broker
# version even when the role assignment actually succeeds (Task A's report) —
# don't let that abort this script under `set -e`, and don't trust the
# command's own success/failure text either; verify with getClient below.
mosquitto_ctrl $CTRL_OPTS dynsec addClientRole backend backend 10 || true

echo "== verifying: backend client has the backend role =="
CLIENT_INFO="$(mosquitto_ctrl $CTRL_OPTS dynsec getClient backend)"
echo "$CLIENT_INFO"
# NOTE: getClient's role line reads "Roles:       backend (priority: 10)" —
# NOT "Rolename: ..." (that's getRole's format for a role's own ACL dump).
# Verified against a live broker; don't "fix" this to Rolename by pattern-matching docs alone.
case "$CLIENT_INFO" in
  *"Roles:"*"backend"*) : ;;
  *)
    echo "ERROR: 'backend' client does not have the 'backend' role assigned — addClientRole may have genuinely failed. See output above." >&2
    exit 1
    ;;
esac

echo "== done. Per-SmartBox client creation/deletion is a separate task (Task D), not scripted here. Manually, if ever needed: =="
echo "  mosquitto_ctrl $CTRL_OPTS dynsec createClient <username>"
echo "  mosquitto_ctrl $CTRL_OPTS dynsec setClientPassword <username> <password>"
echo "  mosquitto_ctrl $CTRL_OPTS dynsec addClientRole <username> smartbox 10"
