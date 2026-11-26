# LensDrop H5 clients

Android source includes H4 photo/QR behavior plus H5 staff pairing and bounded LAN/cloud print requests. Windows Print Station source is in print-station/. Neither native application was compiled locally; use the manual workflows and read ../docs/h5/LensDrop_H5_Setup_Guide.html.

The existing Python desktop photo uploader is unchanged. It is not the Windows Print Station and does not inherit Android new-only Watch Folder behavior. Its 23 tests passed against the H5 local cloud.

No FTP receiver, camera SDK adapter or iOS application is included. Preserve all originals, signing keys, permissions, queue state and station identity during upgrades.
