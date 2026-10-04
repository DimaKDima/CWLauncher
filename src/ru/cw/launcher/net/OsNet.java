package ru.cw.launcher.net;

import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Лимит канала через объект задания Windows. Держит открытым служебный процесс,
 * потому что ограничение живёт, пока жив дескриптор задания.
 * Дочерние процессы из задания не забираются: у задания стоит silent breakaway.
 */
final class OsNet {

    private static final Object LOCK = new Object();
    private static Process proc;
    private static BufferedWriter out;
    private static final BlockingQueue<String> REPLIES = new LinkedBlockingQueue<>();

    private OsNet() {
    }

    /** bps == 0 снимает потолок. dscp == 0 снимает метку. breakaway — дочерние процессы не наследуют лимит. */
    static void set(long pid, long bps, int dscp, boolean breakaway) {
        if (pid <= 0 || !Utils.isWindows()) return;
        synchronized (LOCK) {
            try {
                ensure();
                out.write("set " + pid + " " + Math.max(0, bps) + " " + Math.max(0, Math.min(63, dscp))
                        + " " + (breakaway ? 1 : 0));
                out.newLine();
                out.flush();
                String reply = REPLIES.poll(6, TimeUnit.SECONDS);
                if (reply == null) {
                    Log.warn("Лимит сети Windows не ответил");
                    drop();
                } else if (reply.startsWith("err")) {
                    Log.warn("Лимит сети Windows: " + reply);
                } else {
                    Log.info("Лимит сети Windows: " + reply);
                }
            } catch (Exception e) {
                Log.warn("Лимит сети Windows не применён: " + Log.reason(e));
                drop();
            }
        }
    }

    private static void ensure() throws Exception {
        if (proc != null && proc.isAlive() && out != null) return;
        drop();
        Path script = Files.createTempFile("cw-net-", ".ps1");
        script.toFile().deleteOnExit();
        Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        proc = pb.start();
        out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> read(proc), "cw-net-job");
        reader.setDaemon(true);
        reader.start();
        Thread err = new Thread(() -> drain(proc), "cw-net-job-err");
        err.setDaemon(true);
        err.start();
    }

    private static void read(Process process) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) REPLIES.add(line.trim());
            }
        } catch (Exception ignored) {
        }
    }

    private static void drain(Process process) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) Log.warn("Лимит сети: " + line.trim());
            }
        } catch (Exception ignored) {
        }
    }

    private static void drop() {
        if (proc != null) proc.destroy();
        proc = null;
        out = null;
        REPLIES.clear();
    }

    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            try {
              Add-Type -TypeDefinition @'
            using System;
            using System.Runtime.InteropServices;
            public class CwJob {
              [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
              public static extern IntPtr CreateJobObject(IntPtr a, string name);
              [DllImport("kernel32.dll", SetLastError=true)]
              public static extern bool SetInformationJobObject(IntPtr hJob, int cls, IntPtr info, uint cb);
              [DllImport("kernel32.dll", SetLastError=true)]
              public static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
              [DllImport("kernel32.dll", SetLastError=true)]
              public static extern bool AssignProcessToJobObject(IntPtr hJob, IntPtr hProcess);
              [DllImport("kernel32.dll", SetLastError=true)]
              public static extern bool CloseHandle(IntPtr h);
              [DllImport("kernel32.dll")]
              public static extern int GetLastError();
            }
            '@
            } catch {
              Write-Output ('err ' + $_.Exception.Message)
              exit 1
            }
            $jobs = @{}
            function Set-Breakaway([IntPtr]$job) {
              $size = 144
              $mem = [Runtime.InteropServices.Marshal]::AllocHGlobal($size)
              try {
                [Runtime.InteropServices.Marshal]::Copy([byte[]]::new($size), 0, $mem, $size)
                [Runtime.InteropServices.Marshal]::WriteInt32($mem, 16, 0x8000)
                $ok = [CwJob]::SetInformationJobObject($job, 9, $mem, [uint32]$size)
                if (-not $ok) { throw ('breakaway ' + [CwJob]::GetLastError()) }
              } finally { [Runtime.InteropServices.Marshal]::FreeHGlobal($mem) }
            }
            function Set-Rate([IntPtr]$job, [int64]$bps, [int]$dscp) {
              $flags = 1
              if ($bps -gt 0) { $flags = $flags -bor 2 }
              if ($dscp -gt 0) { $flags = $flags -bor 4 }
              $mem = [Runtime.InteropServices.Marshal]::AllocHGlobal(16)
              try {
                [Runtime.InteropServices.Marshal]::Copy([byte[]]::new(16), 0, $mem, 16)
                [Runtime.InteropServices.Marshal]::WriteInt64($mem, 0, $bps)
                [Runtime.InteropServices.Marshal]::WriteInt32($mem, 8, $flags)
                if ($dscp -gt 0) { [Runtime.InteropServices.Marshal]::WriteByte($mem, 12, [byte]$dscp) }
                $ok = [CwJob]::SetInformationJobObject($job, 32, $mem, [uint32]16)
                if (-not $ok) { throw ('rate ' + [CwJob]::GetLastError()) }
              } finally { [Runtime.InteropServices.Marshal]::FreeHGlobal($mem) }
            }
            while ($null -ne ($line = [Console]::In.ReadLine())) {
              $parts = $line.Trim().Split(' ')
              if ($parts.Length -lt 5 -or $parts[0] -ne 'set') { Write-Output 'err bad'; continue }
              try {
                $procId = [int]$parts[1]
                $bps = [int64]$parts[2]
                $dscp = [int]$parts[3]
                $needBreak = [int]$parts[4]
                $key = [string]$procId
                if ($jobs.ContainsKey($key)) {
                  Set-Rate $jobs[$key] $bps $dscp
                } else {
                  $job = [CwJob]::CreateJobObject([IntPtr]::Zero, $null)
                  if ($job -eq [IntPtr]::Zero) { throw ('create ' + [CwJob]::GetLastError()) }
                  if ($needBreak -eq 1) { Set-Breakaway $job }
                  Set-Rate $job $bps $dscp
                  $h = [CwJob]::OpenProcess([uint32]0x101, $false, $procId)
                  if ($h -eq [IntPtr]::Zero) { throw ('open ' + [CwJob]::GetLastError()) }
                  $assigned = [CwJob]::AssignProcessToJobObject($job, $h)
                  $err = [CwJob]::GetLastError()
                  [CwJob]::CloseHandle($h) | Out-Null
                  if (-not $assigned) { throw ('assign ' + $err) }
                  $jobs[$key] = $job
                }
                Write-Output ('ok ' + $key + ' ' + $bps + ' ' + $dscp)
              } catch {
                Write-Output ('err ' + $_.Exception.Message)
              }
            }
            """;
}
