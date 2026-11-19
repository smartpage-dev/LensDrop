# H3.1 - Recover from admin_key_required / 503

**Do not clear event data or reinitialize storage to fix a missing administrator key.**

## 1. Generate or restore the correct secret

First use your existing saved administrator key. This is LensDrop's private admin key, not your Hostinger account password or an API token from another service.

When no valid key was previously configured, open `tools/Hostinger_Setup_Helper.html` locally on your computer. Choose **Generate administrator key only**, save the value in your password manager, then **Copy key value only**. This generates no storage identity and makes no network requests. Do not upload generated secret files or paste real secrets into chats or screenshots.

Alternative in a local Terminal with Node installed:

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

This prints 64 hexadecimal characters. Copy the generated value; do not copy this command into hPanel's value field. With the package extracted you can alternatively run `npm run generate:admin-key`; its output includes the variable name, so copy only the portion after `=` into the Value field.

## 2. Save the runtime environment setting

Open your website's Hostinger Dashboard, then **Environment variables**.

| Setting | Required action |
|---|---|
| Name | `LENSDROP_ADMIN_KEY` (exact spelling) |
| Value | Your saved or locally generated private key; at least 32 characters, recommended 64 hex characters |
| Quotes | No surrounding quotes or pasted line breaks |
| Save | Click Apply changes, then restart the application |

Do not fill `ADMIN_KEY` instead, paste a placeholder, or edit only `sample.env` / `sample.hostinger.env`. The application does not automatically load a `.env` file. hPanel must pass the actual runtime environment value to Node.

When a variable is already shown in hPanel, verify it was actually saved and applied to the running process; restore the saved valid value rather than assuming a displayed placeholder is a credential.

## 3. Deploy the corrected startup package

Use **LensDrop_Hostinger_Cloud_v0.2-alpha_H3_1_StartupFix.zip** through Node.js Web App redeploy. Do not upload the full Programs and Docs outer archive. It is not a static/PHP File Manager deployment.

Settings remain: framework Other; Node 22.x at least 22.13; root/output `.`; entry `server.js`; build `npm run build`; start `npm start`. Leave PORT to the hosting platform. Node 24 is declared by the package but not tested in this hotfix.

For an existing installation, keep the saved data path and storage ID and set initialization to 0. Do not stop at a successful build: the build deliberately does not validate/open production storage.

## 4. Verify the runtime

Open your site's `/health` endpoint. Expect HTTP 200 and `ok: true`; after this hotfix the version is `0.2.0-alpha.4-hostinger.3.1`. Open `/admin` and connect using the same private key.

Read the NEW log after restart, not only old retained log entries. A successful boot emits `lensdrop_ready`. Under a socket-based host, a null port with `transport: managed-socket` is normal and does not mean a failed listener.

A successful health check is only a startup check, not proof of persistent storage across redeploys or backups.

## 5. Preserve the other required settings

| Variable | Existing installation | Brand-new, confirmed empty installation |
|---|---|---|
| `LENSDROP_DATA_DIR` | Restore exact saved private persistent absolute path | Obtain a real private persistent path from your host; do not invent one |
| `LENSDROP_STORAGE_ID` | Reuse original saved 32-lowercase-hex identifier | Generate once with the full new-install helper and save privately |
| `LENSDROP_INITIALIZE_STORAGE` | Keep `0` | Use `1` only after confirming the correct folder is new and empty; after successful initial startup set back to `0` and restart |

If the application has ever contained real events, or you are uncertain whether the folder is empty, do not use initialization to recover it. Restore the correct path and backup instead.

## 6. Interpret the next error, if any

| Runtime code | Meaning / safe next action |
|---|---|
| `admin_key_required` | Key is absent, too short or an example; correct the runtime value and restart |
| `absolute_data_dir_required` | Set a real absolute private persistent data path |
| `deployment_tree_forbidden` | Do not store data under public_html, hbuilds or nodejs; confirm an alternative path with the host |
| `storage_id_required` | Restore the saved storage identifier; generate only for a new installation |
| `storage_identity_mismatch` | Stop and restore matching configuration; never overwrite the marker |
| `storage_not_initialized` | Verify path and whether this is genuinely a new empty site before initialization |
| `database_missing` | Restore the complete correct data directory; do not create a new empty gallery over lost data |
| `invalid_port` | Do not set a made-up port; use the host's valid numeric PORT assignment |

The current supplied log stops at the admin-key gate. It does not establish that the later storage checks have already passed.

## Limits and rollback

This hotfix changes startup handling and recovery tools, not schema 3 or slideshow behavior. No APK, camera adapter or live hosting verification is added. Retained original and H1/H2/H3 guides remain available. These hotfix instructions take precedence for the startup error.

If rollback is required, stop the process, preserve current data and use matching code/data versions. H3.1 does not itself require downgrading or reinitializing an H3 database. A return to H2 or earlier still requires the original pre-H3 full-backup procedure.
