using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

class Launcher {
    [STAThread]
    static int Main(string[] args) {
        string tempJar = Path.Combine(Path.GetTempPath(), "FW0_runtime_" + Process.GetCurrentProcess().Id + ".jar");
        try {
            using (Stream res = Assembly.GetExecutingAssembly().GetManifestResourceStream("FW0.jar")) {
                if (res == null) {
                    MessageBox.Show("Embedded FW0.jar resource not found.", "Launch Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return 1;
                }
                using (FileStream fs = new FileStream(tempJar, FileMode.Create, FileAccess.Write)) {
                    res.CopyTo(fs);
                }
            }

            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = "javaw.exe";
            psi.Arguments = "-jar \"" + tempJar + "\"";
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WorkingDirectory = AppDomain.CurrentDomain.BaseDirectory;

            using (Process p = Process.Start(psi)) {
                if (p != null) {
                    p.WaitForExit();
                    return p.ExitCode;
                }
            }
            return 0;
        } catch (Exception ex) {
            MessageBox.Show("Java Runtime (javaw.exe) の起動に失敗しました:\n" + ex.Message, "FW0 Launcher", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        } finally {
            try {
                if (File.Exists(tempJar)) {
                    File.Delete(tempJar);
                }
            } catch {
            }
        }
    }
}
