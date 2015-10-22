(ns buergeramt-sniper.core
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [schema.core :as s]
            [clj-time.format :as tf]
            [clj-yaml.core :as yaml]
            [buergeramt-sniper.scheduler :as scheduler]
            [buergeramt-sniper.loader :as loader]
            [buergeramt-sniper.api :as api]
            [clojure.tools.cli :as cli]
            [clojure.core.async :refer [<!!]]
            [clj-http.conn-mgr :as conn-mgr]
            [clojure.string :as str])
  (:gen-class))

(s/defrecord RunParams
  [options :- {s/Keyword s/Any}
   user-form-params :- {s/Keyword s/Str}])

(s/defschema SniperYaml
  {:Nachname                                        s/Str
   :EMail                                           s/Str
   (s/optional-key :telefonnummer_fuer_rueckfragen) s/Str
   (s/optional-key :Anmerkung)                      s/Str})

(defn read-yaml-config [file]
  (s/validate SniperYaml (yaml/parse-string (slurp file))))

(defn init
  "Configure system and start it"
  [options user-form-params]
  (log/info "Starting system...")
  (log/spy :info user-form-params)
  (let [system (component/system-map
                 :run-params (strict-map->RunParams
                               {:options          options
                                :user-form-params user-form-params})
                 :loader (loader/strict-map->Loader
                           {:use-caching        false
                            :use-local-for-post nil #_"http://localhost:8000"
                            :connection-manager (some->> (:socks options)
                                                         (apply conn-mgr/make-socks-proxied-conn-manager))})
                 :scheduler (scheduler/strict-map->Scheduler {}))
        started-system (component/start system)]
    (log/info "System started.")
    started-system))

(defn run
  "Main function that is called after the system is started"
  [system]
  (log/info " Running... ")
  (log/spy system)
  (api/start-trying system))

(defn parse-date [date-str]
  (tf/parse (tf/formatters :date) date-str))

(defn parse-socks [host:port]
  (let [[host port] (str/split host:port #":")]
    [host (Integer/parseInt port)]))

(defn init-debug
  "Provide run-params which are normally supplied wia command-line, then run init"
  []
  (let [options {
                 ;:base-url "https://service.berlin.de/dienstleistung/121482/" ; Grünes Kennzeichen beantragen
                 ;:base-url   "https://service.berlin.de/dienstleistung/326423/" ; Abholung von sichergestellten Fahrzeugen
                 ;:base-url   "https://service.berlin.de/dienstleistung/120686/" ; Anmeldung einer Wohnung
                 :base-url   "https://service.berlin.de/dienstleistung/121598/" ; Umschreibung einer ausländischen Fahrerlaubnis
                 :start-date (parse-date "2015-10-06")
                 ;:end-date (parse-date "2015-10-08")
                 :socks      (parse-socks "localhost:9050")
                 :dry-run    true
                 }
        user-form-params (read-yaml-config "sniper.yaml")]
    (init options user-form-params)))

(def CLI_OPTIONS
  [["-s" "--start-date <YYYY-MM-dd>" "Start of the desired date interval (inclusive)."
    :parse-fn parse-date]
   ["-e" "--end-date <YYYY-MM-dd>" "End of the desired date interval (inclusive)."
    :parse-fn parse-date]
   ["-c" "--config CONFIG_FILE" "Config file name."
    :default "sniper.yaml"]
   [nil "--dry-run" "Look for the time, but don't book it when it's found."]
   [nil "--socks <host:port>" "Use SOCKS proxy for scanning (to avoid banning). The actual booking request will be made directly."
    :parse-fn parse-socks]
   ["-h" "--help" "Show this help message"]])

(defn usage [options-summary]
  (->> ["Buergeramt-sniper is a tool for finding and booking appointments with Berlin public services (https://service.berlin.de)"
        ""
        "Usage: lein run -- [options] service-url"
        ""
        "Options:"
        options-summary
        ""]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  "Entry point when run from a command line"
  [& args]
  (let [{:keys [options arguments errors summary] :as cli-opts} (cli/parse-opts args CLI_OPTIONS)]
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (let [base-url (first arguments)
          user-form-params (read-yaml-config (:config options))
          system (init (assoc options :base-url base-url)
                       user-form-params)]
      (try (<!! (run system))
           (finally (component/stop system))))))
