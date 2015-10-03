(ns buergeramt-sniper.loader-test
  (:require [clojure.test :refer :all]
            [buergeramt-sniper.loader :refer :all]))

(deftest can-replace-host-addr
  (is (= "http://localhost:8000/foo/bar.php?a=1"
         (replace-host-addr "https://example.com/foo/bar.php?a=1"
                            "http://localhost:8000")))
  (is (= "http://localhost:8000/"
         (replace-host-addr "https://example.com/"
                            "http://localhost:8000"))))
