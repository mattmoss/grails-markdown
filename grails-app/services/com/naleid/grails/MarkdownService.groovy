package com.naleid.grails

import static org.pegdown.Extensions.*

import java.util.concurrent.locks.ReentrantLock

import org.pegdown.PegDownProcessor

import com.overzealous.remark.Options
import com.overzealous.remark.Remark

class MarkdownService {

	def grailsApplication

	static boolean transactional = false

	private PegDownProcessor processor

	private Remark remark

	private String baseUri

	private static Options remarkOptions
	private static int pegdownExtensions = 0

	private final ReentrantLock processorLock = new ReentrantLock()

	/**
	 * Converts the provided Markdown into HTML
	 *
	 * <p>By default this method uses the shared configuration.  However, the default configuration can
	 * be overridden by passing in a map or map-like object as the second argument.  With a custom
	 * configuration, a new Pegdown processor is created <strong>every call to this method!</strong></p>
	 *
	 * @param text Markdown-formatted text
	 * @param conf If provided, creates a custom pegdown with unique settings just for this instance
	 * @return HTML-formatted text
	 */
    String markdown(text, conf = null) {
		// lazily created, so we call the method directly
    	PegDownProcessor p = getProcessor(conf)
		String result
		// we have to lock, because pegdown is not thread-safe<
		try {
			processorLock.lock()
			result = p.markdownToHtml((String)text)
		} finally {
			processorLock.unlock()
		}
		result
    }

	/**
	 * Converts the provided HTML back to Markdown
	 *
	 * <p>By default this method uses the shared configuration.  However, the default configuration can
	 * be overridden by passing in a map or map-like object as the second argument.  With a custom
	 * configuration, a new Remark is created <strong>every call to this method!</strong></p>
	 *
	 * @param text HTML-formatted text
	 * @param customBaseUri Override the default base URL
	 * @param conf If provided, creates a custom remark with unique settings just for this instance
	 * @return Markdown-formatted text
	 */
	String htmlToMarkdown(text, customBaseUri = "", conf = null) {
		// lazily created, so we call the method directly
		Remark r = getRemark(conf)
		if(baseUri && customBaseUri == '') {
			customBaseUri = baseUri
		}
		if(customBaseUri.size() > 0 && customBaseUri[-1] != '/') {
			customBaseUri += '/'
		}
		r.convertFragment(text, customBaseUri)
	}

	/**
	 * Utility method to strip untrusted HTML from markdown input.
	 *
	 * <p>Works by simply running the text through pegdown and back through remark.</p>
	 *
	 * <p>By default this method uses the shared configuration.  However, the default configuration can
	 * be overridden by passing in a map or map-like object as the second argument.  With a custom
	 * configuration, new processing engines are created <strong>every call to this method!</strong></p>
	 *
	 * @param text Markdown-formatted text
	 * @param conf If provided, creates custom remark and pegdown with unique settings for this instance
	 * @return Sanitized Markdown-formatted text
	 */
	String sanitize(text, conf = null) {
		htmlToMarkdown(markdown(text, conf), '', conf)
	}

	/**
	 * Returns or creates the Pegdown processor instance used for conversion
	 * @param conf Optional configuration Map to create a custom processor
	 * @return PegdownProcessor instance
	 */
	PegDownProcessor getProcessor(conf = null) {
		def result
		if(conf != null) {
			Map opts = getConfigurations(conf)
			result = new PegDownProcessor((int)opts.pegdownExtensions)
		} else {
			if(processor == null) {
				setupConfigurations()
				processor = new PegDownProcessor(pegdownExtensions)
			}
			result = processor
		}
		result
	}

	/**
	 * Returns or creates the Remark instance used for conversion
	 * @param conf Optional configuration Map to create a custom remark.
	 * @return Remark instance
	 */
	Remark getRemark(conf = null) {
		def result
		if(conf != null) {
			Map opts = getConfigurations(conf)
			result = new Remark((Options)opts.remarkOptions)
		} else {
			if(remark == null) {
				setupConfigurations()
				remark = new Remark(remarkOptions)
			}
			result = remark
		}
		result
	}

	//------------------------------------------------------------------------

	// sets up the configuration for markdown and pegdown
	private void setupConfigurations() {
		if(remarkOptions == null) {
			def conf = grailsApplication.config.markdown
			def opts = getConfigurations(conf)
			remarkOptions = (Options)opts.remarkOptions
			pegdownExtensions = (int)opts.pegdownExtensions
			baseUri = opts.baseUri
		}
	}

	// this is where the configuration actually happens
	// conf can be set via any map-like object
	private Map getConfigurations(conf) {
		Map result = [remarkOptions: Options.pegdownBase(), pegdownExtensions: 0, baseUri: null]

		if(conf) {
			def all = conf.all as Boolean
			def pdExtension = { result.pegdownExtensions = result.pegdownExtensions | it }
			def enableIf = { test, rm, pd ->
				if(all || test) {
					result.remarkOptions[rm] = true
					pdExtension(pd)
				}
			}
			enableIf(conf.abbreviations, 'abbreviations', ABBREVIATIONS)
			enableIf(conf.hardwraps, 'hardwraps', HARDWRAPS)
			enableIf(conf.definitionLists, 'definitionLists', DEFINITIONS)
			enableIf(conf.autoLinks, 'autoLinks', AUTOLINKS)
			enableIf(conf.smartQuotes, 'reverseSmartQuotes', QUOTES)
			enableIf(conf.smartPunctuation, 'reverseSmartPunctuation', SMARTS)
			enableIf(conf.smart, 'reverseAllSmarts', SMARTYPANTS)

			if(all || conf.fencedCodeBlocks) {
				result.remarkOptions.fencedCodeBlocks = Options.FencedCodeBlocks.ENABLED_TILDE
				pdExtension(FENCED_CODE_BLOCKS)
			}

			if(conf.removeHtml) {
				result.remarkOptions.tables = Options.Tables.REMOVE
				pdExtension(SUPPRESS_ALL_HTML)
			}

			if(all || conf.tables) {
				result.remarkOptions.tables = Options.Tables.MULTI_MARKDOWN
				pdExtension(TABLES)
			} else if(conf.removeTables) {
				result.remarkOptions.tables = Options.Tables.REMOVE
			}

			if(conf.customizeRemark) {
				def opts = conf.customizeRemark(result.remarkOptions)
				if(opts instanceof Options) {
					result.remarkOptions = opts
				}
			}

			if(conf.customizePegdown) {
				def exts = conf.customizeRemark(result.pegdownExtensions)
				if(exts instanceof Integer) {
					result.pegdownExtensions = (int)exts
				}
			}

			// only disable baseUri if it is explicitly set to false
			//noinspection GroovyPointlessBoolean
			if(conf.baseUri != false) {
				if(conf.baseUri) {
					result.baseUri = conf.baseUri
				} else {
					result.baseUri = grailsApplication.config.grails.serverURL
				}
			}
		}

		return result
	}
}
