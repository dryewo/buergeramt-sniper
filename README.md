# buergeramt-sniper

A useful tool that helps to get an appointment at Berlin public services (https://service.berlin.de/dienstleistungen/)

Sometimes you need to get an appointment at Berlin Bürgeramt, for example when you have just arrived in Germany and need to register in order to be able to open a bank account in order to be able to receive your salary. It usually has to be done within first 10 days of the month.

This service is called **"Anmeldung einer Wohnung"**.

And when you go to https://service.berlin.de/dienstleistung/120686/ to pick a time to visit, you discover that the earliest available time is 2 months from now, which does not help too much — you need it now, not after 2 months.

There are 3 real options:

- Go to the nearest Bürgeramt at 7am, stand ~2 hours in the huge line and hope that there someone won't show up on his time and you get served instead of him.
- Continuously refresh (F5) the calendar page, so that if someone cancels his appointment, you are able to take it very quickly. But be aware, there are many people doing this. Usually takes several hours until you succeed.
- Use **buergeramt-sniper**, that will do all the page F5ing and form filling for you.

## Installation

- Install [Leiningen](http://leiningen.org/) to be able to use Clojure.
- Clone the repo.

It is also highly recommended to use [Tor](https://www.torproject.org/docs/documentation.html.en) for IP address cycling, because otherwise there is a chance of being banned for too many requests (HTTP 429).

    $ brew install tor

or

    $ sudo apt-get install -y tor

## Precautions

- Use `--dry-run` parameter to try things out
- Use Tor and `--socks` parameter for anonimizing

## Usage

First go to (https://service.berlin.de/dienstleistungen/) and choose the service that you are trying to get an appointment to.
The page should have **Termin berlinweit suchen** button on the right.
Copy its URL (should look like `https://service.berlin.de/dienstleistung/120686/`).

Try to find at least one available time for this service and open the form page. Look into page source and find the names of the necessary text inputs. Usually they look like this:

```
<input type="text" class="span12" name="Nachname" id="Nachname" value="" maxlength="50" tabindex="5">
```

Usually there are 3 important inputs, namely:

- `Nachname`
- `EMail`
- either `Anmerkung` or `telefonnummer_fuer_rueckfragen`

Create a file `sniper.yaml` in the project root and put there the strings that you are going to provide to that form when the appointment will be found for you, for example:

```
Nachname: Michael Jackson
EMail: michael.jackson@example.com
telefonnummer_fuer_rueckfragen: "+4915781399988"
```

Before starting the **buergeramt-sniper**, run Tor in a separate terminal session:

    $ tor MaxCircuitDirtiness 60

Tor starts to listen on port 9050 by default, works as a SOCKS proxy and changes IPs every 60 seconds. After you're done, you can stop it with `Ctrl-C`.

Then finally run the **buergeramt-sniper** and wait for it to find an appointment for you.

    $ lein run https://service.berlin.de/dienstleistung/120686/

See **Options** and **Examples** sections for more information.

## Options

```
Usage: lein run -- [options] service-url

Options:
  -s, --start-date <YYYY-MM-dd>               Start of the desired date interval (inclusive).
  -e, --end-date <YYYY-MM-dd>                 End of the desired date interval (inclusive).
  -c, --config CONFIG_FILE       sniper.yaml  Config file name.
      --dry-run                               Look for the time, but don't book it when it's found.
      --socks <host:port>                     Use SOCKS proxy for scanning (to avoid banning). The actual booking request will be made directly.
  -h, --help                                  Show this help message
```

## Examples

```
$ lein run -- --socks localhost:9050 "https://service.berlin.de/dienstleistung/120686" --dry-run -s 2015-10-06 -e 2015-10-08
# Or
$ java -jar target/uberjar/buergeramt-sniper.jar --socks localhost:9050 "https://service.berlin.de/dienstleistung/120686" --dry-run -s 2015-10-06 -e 2015-10-08
```

## Known issues and limitations

1. No scanning of all available dates, just those which are seen on the initial calendar page (usually the current month and the month after). But taking into account the most requested use case (need an appointment in the next couple of days) that is enough. Also it reduces the number of necessary requests.

## Donations

If you like this project, the best thing you can do for me is learn Clojure. Clojure is awesome, I want more people to do it.

Long live Clojure!

## License

Copyright © 2015 Dmitrii Balakhonskii

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
