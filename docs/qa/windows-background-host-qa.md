# Windows Background Host QA

This checklist validates the optional per-user Rust library background host.
It intentionally registers a real Task Scheduler job and must only be run
manually on a Windows account and library root chosen for the test. Automated
and package QA must use `-PlanOnly` and must not register the task.

## Preconditions

1. Build or extract the Rust-native Windows package.
2. Choose one or more test library folders that may be scanned.
3. Close Danmaku and confirm port 8686 is not owned by an unrelated process.
4. Back up `%LOCALAPPDATA%\Danmaku\server` if the account contains data
   that cannot be recreated.

In the examples, set `$package` to the extracted package directory and
replace the roots with test folders:

```powershell
$package = "C:\Temp\danmaku-player-0.1.0-windows-x64"
$manager = Join-Path $package "manage-rust-library-background-host.ps1"
& $manager -Action Install -LibraryRoot "D:\TestAnime" -PlanOnly
```

Confirm the plan reports task `\Danmaku\Library Server`, loopback URL
`http://127.0.0.1:8686`, the intended roots, current-user logon trigger,
15-second delay, IgnoreNew policy, and three one-minute restart attempts.

## Install And Ownership

1. Run the real install:

   ```powershell
   & $manager -Action Install -LibraryRoot "D:\TestAnime"
   & $manager -Action Status
   ```

2. In Task Scheduler, verify the task is under `\Danmaku`, uses the current
   user with an interactive, limited token, triggers at that user's logon, and
   has no stored password or elevation.
3. Verify `%LOCALAPPDATA%\Danmaku\server\background-host.json` has
   schema 1, the expected task/URL/roots, and no token or provider secret.
4. Open `http://127.0.0.1:8686/api/server/status` and verify the server reports
   `headless-server`.
5. Start the native player. Verify Settings labels the server as background
   managed and does not allow folder, credential, restart, or stop actions.
6. Close the player and verify the server remains reachable.

## Lifecycle And Roots

1. Run `-Action Stop`; verify the task becomes ready/stopped and the health
   endpoint closes.
2. Run `-Action Start`; verify health and catalog access return.
3. Preview and then apply multiple roots:

   ```powershell
   & $manager -Action SetRoots -LibraryRoot "D:\TestAnime" `
     -LibraryRoot "E:\MoreAnime" -PlanOnly
   & $manager -Action SetRoots -LibraryRoot "D:\TestAnime" `
     -LibraryRoot "E:\MoreAnime"
   ```

4. Verify the task restarts once and both roots appear in the config/catalog.
5. If mapped drives are in scope, sign out and back in once with a root
   temporarily unavailable. Verify the runner logs that it is waiting and the
   server starts after the drive becomes available.

Logs are written beside the server data as
`background-host-runner.log`, `background-host.stdout.log`, and
`background-host.stderr.log`.

## Uninstall And Cleanup

Run:

```powershell
& $manager -Action Uninstall
& $manager -Action Status
```

Verify the scheduled task, background marker, and
`%LOCALAPPDATA%\Programs\Danmaku\LibraryServer` are removed. Verify the
database, protected settings, and other contents of
`%LOCALAPPDATA%\Danmaku\server` remain. The player may again own its
packaged child server on its next local launch.
