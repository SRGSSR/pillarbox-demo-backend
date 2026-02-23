package ch.srgssr.pillarbox.backend.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Extension function to obtain a logger for the given class type.
 *
 * @return A [Logger] instance associated with the class.
 */
inline fun <reified T> T.logger(): Logger = LoggerFactory.getLogger(T::class.java)

/**
 * Logs a trace message if the trace level is enabled for the logger.
 *
 * @param e An optional [Throwable] to be logged alongside the message.
 * @param lazyMessage A lambda function that generates the log message only if trace logging is enabled.
 */
inline fun Logger.trace(
  e: Throwable? = null,
  lazyMessage: () -> String,
) = isTraceEnabled.takeIf { it }?.let { trace(lazyMessage(), e) }

/**
 * Logs a debug message if the debug level is enabled for the logger.
 *
 * @param e An optional [Throwable] to be logged alongside the message.
 * @param lazyMessage A lambda function that generates the log message only if debug logging is enabled.
 */
inline fun Logger.debug(
  e: Throwable? = null,
  lazyMessage: () -> String,
) = isDebugEnabled.takeIf { it }?.let { debug(lazyMessage(), e) }

/**
 * Logs an info message if the info level is enabled for the logger.
 *
 * @param e An optional [Throwable] to be logged alongside the message.
 * @param lazyMessage A lambda function that generates the log message only if info logging is enabled.
 */
inline fun Logger.info(
  e: Throwable? = null,
  lazyMessage: () -> String,
) = isInfoEnabled.takeIf { it }?.let { info(lazyMessage(), e) }

/**
 * Logs a warning message if the warn level is enabled for the logger.
 *
 * @param e An optional [Throwable] to be logged alongside the message.
 * @param lazyMessage A lambda function that generates the log message only if warn logging is enabled.
 */
inline fun Logger.warn(
  e: Throwable? = null,
  lazyMessage: () -> String,
) = isWarnEnabled.takeIf { it }?.let { warn(lazyMessage(), e) }

/**
 * Logs an error message if the error level is enabled for the logger.
 *
 * @param e An optional [Throwable] to be logged alongside the message.
 * @param lazyMessage A lambda function that generates the log message only if error logging is enabled.
 */
inline fun Logger.error(
  e: Throwable? = null,
  lazyMessage: () -> String,
) = isErrorEnabled.takeIf { it }?.let { error(lazyMessage(), e) }
