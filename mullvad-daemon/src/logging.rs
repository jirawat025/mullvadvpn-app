use std::{
    io,
    path::PathBuf,
    sync::atomic::{AtomicBool, Ordering},
};
use talpid_core::logging::rotate_log;
use tracing_subscriber::{
    self, filter::LevelFilter, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter,
};

#[derive(thiserror::Error, Debug)]
pub enum Error {
    /// Unable to open log file for writing
    #[error("Unable to open log file for writing: {path}")]
    WriteFile {
        path: String,
        #[source]
        source: io::Error,
    },

    #[error("Unable to rotate daemon log file")]
    RotateLog(#[from] talpid_core::logging::RotateLogError),

    #[error("Unable to set logger")]
    SetLoggerError(#[from] log::SetLoggerError),
}

pub const WARNING_SILENCED_CRATES: &[&str] = &["netlink_proto"];
pub const SILENCED_CRATES: &[&str] = &[
    "h2",
    "tokio_core",
    "tokio_io",
    "tokio_proto",
    "tokio_reactor",
    "tokio_threadpool",
    "tokio_util",
    "tower",
    "want",
    "ws",
    "mio",
    "hyper",
    "rtnetlink",
    "rustls",
    "netlink_sys",
    "tracing",
    "hickory_proto",
    "hickory_server",
    "hickory_resolver",
];
const SLIGHTLY_SILENCED_CRATES: &[&str] = &["mnl", "nftnl", "udp_over_tcp"];

// const COLORS: ColoredLevelConfig = ColoredLevelConfig {
//     error: Color::Red,
//     warn: Color::Yellow,
//     info: Color::Green,
//     debug: Color::Blue,
//     trace: Color::Black,
// };

#[cfg(windows)]
const LINE_SEPARATOR: &str = "\r\n";

/// Whether a [log] logger has been initialized.
// the log crate doesn't provide a nice way to tell if a logger has been initialized :(
static LOG_ENABLED: AtomicBool = AtomicBool::new(false);

/// Check whether logging has been enabled, i.e. if [init_logger] has been called successfully.
pub fn is_enabled() -> bool {
    LOG_ENABLED.load(Ordering::SeqCst)
}

pub fn init_logger(
    log_level: log::LevelFilter,
    log_file: Option<&PathBuf>,
    output_timestamp: bool,
) -> Result<(), Error> {
    let level_filter = match log_level {
        log::LevelFilter::Off => LevelFilter::OFF,
        log::LevelFilter::Error => LevelFilter::ERROR,
        log::LevelFilter::Warn => LevelFilter::WARN,
        log::LevelFilter::Info => LevelFilter::INFO,
        log::LevelFilter::Debug => LevelFilter::DEBUG,
        log::LevelFilter::Trace => LevelFilter::TRACE,
    };

    let env_filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::from_default_env().add_directive(level_filter.into()));

    let default_filter = get_default_filter(level_filter);

    let (user_filter, handle) = tracing_subscriber::reload::Layer::new(env_filter);

    let formatter = tracing_subscriber::fmt::layer().with_ansi(true);

    let reg = tracing_subscriber::registry()
        .with(user_filter)
        .with(default_filter);
    if output_timestamp {
        reg.with(
            formatter.with_timer(tracing_subscriber::fmt::time::ChronoUtc::new(
                "%H:%M:%S%.3f".to_string(),
            )),
        )
        .init();
    } else {
        reg.with(formatter.without_time()).init();
    }

    // let new_filter = LevelFilter::TRACE;
    // handle
    //     .modify(|filter| *filter = EnvFilter::new(new_filter.to_string()))
    //     .unwrap();

    if let Some(log_file) = log_file {
        rotate_log(log_file).map_err(Error::RotateLog)?;
    }
    #[cfg(all(target_os = "android", debug_assertions))]
    {
        use android_logger::{AndroidLogger, Config};
        let logger: Box<dyn log::Log> = Box::new(AndroidLogger::new(
            Config::default().with_tag("mullvad-daemon"),
        ));
        top_dispatcher = top_dispatcher.chain(logger);
    }

    LOG_ENABLED.store(true, Ordering::SeqCst);

    Ok(())
}

fn get_default_filter(level_filter: LevelFilter) -> EnvFilter {
    let mut env_filter = EnvFilter::builder().parse("trace").unwrap();
    for silenced_crate in WARNING_SILENCED_CRATES {
        env_filter = env_filter.add_directive(format!("{silenced_crate}=error").parse().unwrap());
    }
    for silenced_crate in SILENCED_CRATES {
        env_filter = env_filter.add_directive(format!("{silenced_crate}=warn").parse().unwrap());
    }

    // NOTE: the levels set here will never be overwritten, since the default filter cannot be
    // reloaded
    for silenced_crate in SLIGHTLY_SILENCED_CRATES {
        env_filter = env_filter.add_directive(
            format!("{silenced_crate}={}", one_level_quieter(level_filter))
                .parse()
                .unwrap(),
        );
    }
    env_filter
}

fn one_level_quieter(level: LevelFilter) -> LevelFilter {
    match level {
        LevelFilter::OFF => LevelFilter::OFF,
        LevelFilter::ERROR => LevelFilter::OFF,
        LevelFilter::WARN => LevelFilter::ERROR,
        LevelFilter::INFO => LevelFilter::WARN,
        LevelFilter::DEBUG => LevelFilter::INFO,
        LevelFilter::TRACE => LevelFilter::DEBUG,
    }
}
