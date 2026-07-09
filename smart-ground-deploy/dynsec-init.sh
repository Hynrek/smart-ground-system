#!/bin/sh
# Creates the two dynsec roles the mqtt-tls-client-auth plan calls for
# (`backend`, `smartbox`) against a RUNNING mosquitto broker.
#
# This script is NOT run automatically by docker-compose — dynsec role/ACL
# management happens over MQTT ($CONTROL/dynamic-security/#), which needs a
# live broker and an authenticated admin session, so it can't run at
# container build/start time the way cert generation can. Run it once,
# manually, after `docker compose up -d mosquitto` and after you've
# retrieved the initial admin password (see README.md "Dynsec bootstrap").
#
# Usage (from a machine with mosquitto_ctrl, e.g. inside the container):
#   docker compose exec mosquitto sh /mosquitto/config/dynsec-init.sh <admin-password>
# or, if mosquitto_ctrl is installed on the host and 8883 is reachable:
#   ./dynsec-init.sh <admin-password> --cafile /path/to/ca.crt -p 8883
#
# ACLs implement the plan's two roles:
#   backend  — read/write devices/#, read/write $CONTROL/# (dynsec admin)
#   smartbox — write-only devices/discovery; read/write scoped to its own
#              devices/<mac>/... tree via the %u (username) substitution
#              pattern — the plan sets username = MAC per SmartBox.
set -eu

ADMIN_PASSWORD="${1:?Usage: dynsec-init.sh <admin-password> [extra mosquitto_ctrl options...]}"
shift

# Defaults suit running this from inside the mosquitto container, talking to
# itself over the internal plaintext listener. Override via extra args (e.g.
# `-p 8883 --cafile /mosquitto/config/certs/ca.crt --cert ... ` from outside).
CTRL_OPTS="-h 127.0.0.1 -p 1883 -u admin -P $ADMIN_PASSWORD $*"

# RECOVERY: this script is NOT idempotent (relies on `set -eu` to abort on the
# first error, e.g. a transient mosquitto_ctrl failure partway through a role's
# ACL list). A rerun after a partial failure will immediately fail at
# `createRole` with "role already exists" and leave that role half-configured.
# To recover: manually delete the half-built role(s) (and any client created
# against them) before rerunning, e.g.:
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteRole backend
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteRole smartbox
#   mosquitto_ctrl $CTRL_OPTS dynsec deleteClient <username>   # if any were created
# No automatic retry/idempotency is implemented here by design — this is a
# one-shot bootstrap script, not a reconciler.

echo "== creating role: backend =="
mosquitto_ctrl $CTRL_OPTS dynsec createRole backend
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientSend    "devices/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientReceive "devices/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend subscribePattern     "devices/#"  allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientSend    '$CONTROL/#' allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend publishClientReceive '$CONTROL/#' allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL backend subscribePattern     '$CONTROL/#' allow 10

echo "== creating role: smartbox =="
mosquitto_ctrl $CTRL_OPTS dynsec createRole smartbox
# Every SmartBox may announce itself on the shared discovery topic...
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientSend "devices/discovery" allow 10
# ...but only read/write its OWN device subtree, keyed by username (= MAC).
# SECURITY: the "%u" below is substituted with the connecting client's dynsec
# username. This is only a safe per-device boundary because dynsec usernames
# are admin-assigned here (via `createClient`, not client-supplied). If a
# future provisioning step lets a device's username be attacker-influenced or
# unvalidated, a username containing MQTT wildcard characters ("+" or "#")
# would turn "devices/%u/#" into "devices/+/#" or "devices/#", defeating the
# per-device isolation this ACL exists to enforce. Whoever implements the
# backend's device-provisioning/user-lifecycle code MUST validate that
# smartbox-role usernames are MAC-address-formatted (or otherwise free of
# "+"/"#"/wildcard characters) before calling `createClient`.
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientSend    "devices/%u/#" allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox publishClientReceive "devices/%u/#" allow 10
mosquitto_ctrl $CTRL_OPTS dynsec addRoleACL smartbox subscribePattern     "devices/%u/#" allow 10

echo "== done. Next (Task B/C, or manually): =="
echo "  mosquitto_ctrl $CTRL_OPTS dynsec createClient <username> "
echo "  mosquitto_ctrl $CTRL_OPTS dynsec addClientRole <username> backend|smartbox 10"
