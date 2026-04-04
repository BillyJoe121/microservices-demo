package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"os/signal"

	_ "github.com/lib/pq"

	kingpin "github.com/alecthomas/kingpin/v2"

	"github.com/IBM/sarama"
)

var (
	brokerList        = kingpin.Flag("brokerList", "List of brokers to connect").Default("kafka:9092").Strings()
	topic             = kingpin.Flag("topic", "Topic name").Default("votes").String()
	messageCountStart = kingpin.Flag("messageCountStart", "Message counter start from:").Int()
)

const (
	host     = "postgresql"
	port     = 5432
	user     = "okteto"
	password = "okteto"
	dbname   = "votes"
)

// ---------------------------------------------------------------------------
// Patrón Strategy: ConnectStrategy
//
// Define el contrato para cualquier estrategia de conexión a un servicio
// externo. Una estrategia es simplemente una función que intenta conectar
// y devuelve un error si falla.
//
// Permite cambiar el mecanismo de retry (simple, exponential backoff, etc.)
// sin tocar la lógica específica de cada servicio.
// ---------------------------------------------------------------------------

// ConnectStrategy es el tipo que representa una estrategia de conexión.
// Cada servicio (Kafka, PostgreSQL) proporciona su propia implementación.
type ConnectStrategy func() error

// retryUntilConnected ejecuta la estrategia dada en un bucle hasta que
// tenga éxito (err == nil). Es el contexto del patrón Strategy.
func retryUntilConnected(strategy ConnectStrategy, serviceName string) {
	fmt.Printf("Waiting for %s...\n", serviceName)
	for {
		if err := strategy(); err == nil {
			fmt.Printf("%s connected!\n", serviceName)
			return
		}
	}
}

// ---------------------------------------------------------------------------

func main() {
	db := openDatabase()
	defer db.Close()

	// Estrategia de conexión a PostgreSQL: hacer ping hasta que responda
	retryUntilConnected(func() error {
		return db.Ping()
	}, "postgresql")

	dropTableStmt := `DROP TABLE IF EXISTS votes`
	if _, err := db.Exec(dropTableStmt); err != nil {
		log.Panic(err)
	}

	createTableStmt := `CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) NOT NULL UNIQUE, vote VARCHAR(255) NOT NULL)`
	if _, err := db.Exec(createTableStmt); err != nil {
		log.Panic(err)
	}

	master := getKafkaMaster()
	defer master.Close()

	consumer, err := master.ConsumePartition(*topic, 0, sarama.OffsetOldest)
	if err != nil {
		log.Panic(err)
	}

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt)
	doneCh := make(chan struct{})
	go func() {
		for {
			select {
			case err := <-consumer.Errors():
				fmt.Println(err)
			case msg := <-consumer.Messages():
				*messageCountStart++
				fmt.Printf("Received message: user %s vote %s\n", string(msg.Key), string(msg.Value))

				insertDynStmt := `insert into "votes"("id", "vote") values($1, $2) on conflict(id) do update set vote = $2`
				if _, err := db.Exec(insertDynStmt, *messageCountStart, string(msg.Value)); err != nil {
					log.Panic(err)
				}
			case <-signals:
				fmt.Println("Interrupt is detected")
				doneCh <- struct{}{}
			}
		}
	}()
	<-doneCh
	log.Println("Processed", *messageCountStart, "messages")
}

// openDatabase abre una conexión a PostgreSQL (sin verificar conectividad aún).
// El retry real se hace via retryUntilConnected con la estrategia db.Ping().
func openDatabase() *sql.DB {
	psqlconn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=disable", host, port, user, password, dbname)
	for {
		db, err := sql.Open("postgres", psqlconn)
		if err == nil {
			return db
		}
	}
}

// getKafkaMaster aplica la estrategia de conexión a Kafka:
// intenta crear un consumer hasta que tenga éxito.
func getKafkaMaster() sarama.Consumer {
	kingpin.Parse()
	config := sarama.NewConfig()
	config.Consumer.Return.Errors = true
	brokers := *brokerList

	var master sarama.Consumer

	// Estrategia de conexión a Kafka: crear consumer hasta que no falle
	retryUntilConnected(func() error {
		var err error
		master, err = sarama.NewConsumer(brokers, config)
		return err
	}, "kafka")

	return master
}

