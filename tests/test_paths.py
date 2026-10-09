import pytest

from car_charge_planner import paths

APP = "car-charge-planner"
LOCATIONS = (paths.config_dir, paths.state_dir, paths.cache_dir, paths.data_dir, paths.shared_config_dir, paths.shared_data_dir)


@pytest.mark.linux
def test_on_linux_the_xdg_variables_decide(tmp_path, monkeypatch):
    for name in ("CONFIG", "STATE", "CACHE", "DATA"):
        monkeypatch.setenv(f"XDG_{name}_HOME", str(tmp_path / name.lower()))
    assert [location() for location in LOCATIONS] == [
        tmp_path / "config" / APP,
        tmp_path / "state" / APP,
        tmp_path / "cache" / APP,
        tmp_path / "data" / APP,
        tmp_path / "config",
        tmp_path / "data",
    ]


@pytest.mark.linux
def test_on_linux_without_the_variables_the_dot_directories_in_home_are_used(tmp_path, monkeypatch):
    for name in ("CONFIG", "STATE", "CACHE", "DATA"):
        monkeypatch.delenv(f"XDG_{name}_HOME", raising=False)
    monkeypatch.setenv("HOME", str(tmp_path))
    assert [location() for location in LOCATIONS] == [
        tmp_path / ".config" / APP,
        tmp_path / ".local" / "state" / APP,
        tmp_path / ".cache" / APP,
        tmp_path / ".local" / "share" / APP,
        tmp_path / ".config",
        tmp_path / ".local" / "share",
    ]


@pytest.mark.parametrize("system", ["windows", "macos"])
def test_windows_and_macos_use_their_usual_places(system, tmp_path, monkeypatch):
    if system == "windows":
        from platformdirs.windows import Windows as SystemDirs

        roaming, local = tmp_path / "AppData" / "Roaming", tmp_path / "AppData" / "Local"
        folders = {"CSIDL_APPDATA": roaming, "CSIDL_LOCAL_APPDATA": local}
        monkeypatch.setattr("platformdirs.windows.get_win_folder", lambda name: str(folders[name]))
        expected = [roaming / APP, local / APP, local / APP / "Cache", local / APP, roaming, local]
    else:
        from platformdirs.macos import MacOS as SystemDirs

        monkeypatch.setenv("HOME", str(tmp_path))
        monkeypatch.setenv("USERPROFILE", str(tmp_path))
        support, caches = tmp_path / "Library" / "Application Support", tmp_path / "Library" / "Caches"
        expected = [support / APP, support / APP, caches / APP, support / APP, support, support]
    monkeypatch.setattr(paths, "PlatformDirs", SystemDirs)
    assert [location() for location in LOCATIONS] == expected
