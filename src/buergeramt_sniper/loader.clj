(ns buergeramt-sniper.loader
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojurewerkz.urly.core :as urly]
            [schema.core :as s]
            [pandect.algo.md5 :as md5]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(s/defschema Dom
  "Enlive DOM schema"
  (s/either s/Str
            {:type s/Any
             :data s/Any}
            {:tag     s/Any
             :attrs   s/Any
             :content s/Any}))

(def Href #"^http")

(s/defrecord Loader
  [use-caching :- s/Bool
   use-local-for-post :- (s/maybe Href)
   connection-manager])

(s/defn get-cache-file-path :- s/Str
  [obj]
  (str "cache/" (md5/md5 (str obj))))

(s/defn read-file [file :- s/Str]
  (with-open [in (PushbackReader. (io/reader file))]
    (read in)))

(s/defn write-file [file :- s/Str
                    obj]
  (with-open [out (io/writer file)]
    (binding [*print-dup* true
              *out* out]
      (prn obj))))

(s/defn load-from-cache
  [{:keys [use-caching]} :- Loader
   key-obj]
  (when use-caching
    (let [cache-file-path (get-cache-file-path key-obj)]
      (when (.exists (io/as-file cache-file-path))
        (log/debug "Cache: reading from" cache-file-path)
        (read-file cache-file-path)))))

(s/defn save-to-cache
  [{:keys [use-caching]} :- Loader
   key-obj
   obj]
  (when use-caching
    (let [cache-file-path (get-cache-file-path key-obj)]
      (io/make-parents cache-file-path)
      (log/debug "Cache: writing into" cache-file-path)
      (write-file cache-file-path obj))))

(s/defn resolve-href :- Dom
  "Given a node (probably :a of :form), resolve its :href or :action attr if it is relative,
  return transformed node"
  [base-url :- Href
   node :- Dom]
  (let [resolve-fn #(try (urly/resolve base-url %)
                         (catch Exception _ %))]
    (-> node
        (update-in [:attrs :href] resolve-fn)
        (update-in [:attrs :action] resolve-fn))))

(s/defn parse-html :- [Dom]
  "Parse page body and resolve relative hrefs, return DOM"
  [url :- Href
   body :- s/Str]
  (-> (html/html-snippet body)
      (html/at [#{:a :form}] (partial resolve-href url))))

(s/defn load-page-impl :- [Dom]
  "Load a page (possibly from the cache) and parse it, return DOM or nil"
  [loader :- Loader
   {:keys [method url] :as request-opts} :- {s/Keyword s/Any}]
  (log/debug "Loading" method url)
  (let [cached-response (load-from-cache loader request-opts)
        response (or cached-response (http/request request-opts))
        {:keys [status body error]} response]
    (log/trace [status error])
    (if error
      (log/error "Error:" error)
      (if (not= 200 status)
        (log/error "Status:" status)
        (do
          (when (and (nil? cached-response)
                     (not (re-seq #"localhost" url)))       ; Never cache dev-server responses
            (save-to-cache loader request-opts response))
          (parse-html url body))))))

(def DEFAULT_HEADERS {"User-Agent"    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.101 Safari/537.36"
                      "Cache-Control" "no-cache, no-store"
                      "Pragma"        "no-cache"})

(s/defn replace-host-addr :- Href
  "Replaces protocol and host parts in the url"
  [where :- Href
   with :- Href]
  (let [where-path (urly/path-of where)
        where-tail (if (= "/" where-path)
                     "/"
                     (subs where (.indexOf where where-path)))]
    (str with where-tail)))

(s/defn load-page-get :- [Dom]
  [{:keys [connection-manager] :as loader} :- Loader
   url :- Href]
  (load-page-impl loader {:method             :get
                          :url                url
                          :headers            DEFAULT_HEADERS
                          :connection-manager connection-manager}))

(s/defn load-page-post :- [Dom]
  [{:keys [use-local-for-post] :as loader} :- Loader
   url :- Href
   request-opts :- {s/Keyword s/Any}]
  (load-page-impl loader
                  (merge-with merge
                              request-opts
                              {:method          :post
                               :force-redirects true
                               :url             (if use-local-for-post
                                                  (replace-host-addr url use-local-for-post)
                                                  url)
                               :headers         DEFAULT_HEADERS})))